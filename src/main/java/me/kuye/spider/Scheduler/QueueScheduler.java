package me.kuye.spider.Scheduler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.kuye.spider.entity.Request;

public class QueueScheduler extends Duplicatecheduler {
	private static Logger logger = LoggerFactory.getLogger(QueueScheduler.class);
	private BlockingQueue<Request> queue = new LinkedBlockingQueue<>();

	@Override
	public void doPush(Request request) {
		try {
			queue.put(request);
		} catch (InterruptedException e) {
			logger.info(" throws InterruptedException", e);
		}
	}

	@Override
	public synchronized Request poll() {
		return queue.poll();
	}

}
