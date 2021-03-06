package me.kuye.spider.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.kuye.spider.Scheduler.QueueScheduler;
import me.kuye.spider.Scheduler.Scheduler;
import me.kuye.spider.downloader.HttpDownloader;
import me.kuye.spider.executor.ThreadPool;
import me.kuye.spider.pipeline.Pipeline;
import me.kuye.spider.pipeline.impl.ConsolePipeline;
import me.kuye.spider.processor.Processor;

/**
 * @author xianyijun
 *
 */
public class SimpleSpider implements Task {
	private static Logger logger = LoggerFactory.getLogger(SimpleSpider.class);

	protected HttpDownloader downloader;

	protected Scheduler scheduler = new QueueScheduler();

	protected List<Pipeline> pipelineList = new ArrayList<>();
	protected Request startRequest;

	protected ThreadPool threadPool;

	protected ExecutorService executorService;

	private int threadNum = 1;
	private final AtomicLong pageCount = new AtomicLong(0);

	private LocalDateTime startTime;

	private ReentrantLock urlLock = new ReentrantLock();
	private Condition newUrlCondition = urlLock.newCondition();

	private int sleepTime = 3000;

	private int retryTime = 5000;

	private String domain;

	private Processor processor;

	private volatile boolean running = true;

	private SimpleSpider(Processor processor) {
		this.processor = processor;
	}

	public static SimpleSpider getInstance(Processor processor) {
		return new SimpleSpider(processor);
	}

	@Override
	public void run() {
		initSpider();
		while (!Thread.currentThread().isInterrupted()) {
			Request request = scheduler.poll();
			if (request == null) {
				if (threadPool.getThreadAlive() == 0) {
					break;
				}
				// 等待新的请求连接
				waitNewUrl();
			} else {
				final Request finalRequest = request;
				threadPool.execute(new Runnable() {
					@Override
					public void run() {
						try {
							processRequest(finalRequest, domain);
						} finally {
							pageCount.incrementAndGet();
							signalNewUrl();
						}
					}
				});
			}
		}
		close();
	}

	private void close() {
		LocalDateTime endTime = LocalDateTime.now();
		threadPool.shutdown();
		logger.info(" 爬虫从 " + startTime + " 开始抓取 , 到 " + endTime + " 结束 .");
	}

	private void waitNewUrl() {
		urlLock.lock();
		try {
			if (threadPool.getThreadAlive() == 0) {
				return;
			}
			newUrlCondition.await(sleepTime, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		} finally {
			urlLock.unlock();
		}
	}

	private void signalNewUrl() {
		try {
			urlLock.lock();
			newUrlCondition.signalAll();
		} finally {
			urlLock.unlock();
		}
	}

	private void processRequest(Request request, String domain) {
		Page page = downloader.download(request, domain);
		if (page == null) {
			sleep(retryTime);
			return;
		}
		processor.process(page);
		extractAndAddRequest(page);
		for (Pipeline pipeline : pipelineList) {
			pipeline.process(page.getResult());
		}
		sleep(sleepTime);
	}

	private void extractAndAddRequest(Page page) {
		for (Request request : page.getTargetRequest()) {
			addRequest(request);
		}
	}

	private void addRequest(Request request) {
		scheduler.push(request);
	}

	private void sleep(int sleepTime) {
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @Title: initSpider @Description: 初始化默认组件 @param 参数 @return void
	 *         返回类型 @throws
	 */
	private void initSpider() {
		if (downloader == null) {
			this.downloader = new HttpDownloader();
		}
		this.downloader.setThreaNum(threadNum);
		if (pipelineList.isEmpty()) {
			pipelineList.add(new ConsolePipeline());
		}
		if (threadPool == null || threadPool.isShutdown()) {
			if (executorService != null && !executorService.isShutdown()) {
				this.threadPool = new ThreadPool(threadNum, executorService);
			} else {
				this.threadPool = new ThreadPool(threadNum);
			}
		}
		if (startRequest != null) {
			scheduler.push(startRequest);
		}
		this.startTime = LocalDateTime.now();
	}

	public HttpDownloader getDownloader() {
		return downloader;
	}

	public SimpleSpider setDownloader(HttpDownloader downloader) {
		this.downloader = downloader;
		return this;
	}

	public List<Pipeline> getPipelineList() {
		return pipelineList;
	}

	public ThreadPool getThreadPool() {
		return threadPool;
	}

	public AtomicLong getPageCount() {
		return pageCount;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public ReentrantLock getUrlLock() {
		return urlLock;
	}

	public Condition getNewUrlCondition() {
		return newUrlCondition;
	}

	public int getSleepTime() {
		return sleepTime;
	}

	public int getRetryTime() {
		return retryTime;
	}

	public Processor getProcessor() {
		return processor;
	}

	public Scheduler getScheduler() {
		return scheduler;
	}

	public SimpleSpider setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
		return this;
	}

	public SimpleSpider addPipeline(Pipeline pipeline) {
		this.pipelineList.add(pipeline);
		return this;
	}

	public Request getStartRequest() {
		return startRequest;
	}

	public SimpleSpider setStartRequest(Request startRequest) {
		this.startRequest = startRequest;
		return this;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public SimpleSpider setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
		return this;
	}

	public int getThreadNum() {
		return threadNum;
	}

	public SimpleSpider setThreadNum(int threadNum) {
		this.threadNum = threadNum;
		return this;
	}

	public String getDomain() {
		return domain;
	}

	public SimpleSpider setDomain(String domain) {
		this.domain = domain;
		return this;
	}

}
