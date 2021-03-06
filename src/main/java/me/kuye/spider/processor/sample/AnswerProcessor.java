package me.kuye.spider.processor.sample;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import me.kuye.spider.Scheduler.impl.AnswerRedisScheduler;
import me.kuye.spider.core.Page;
import me.kuye.spider.core.Request;
import me.kuye.spider.core.SimpleSpider;
import me.kuye.spider.dto.answer.AnswerResult;
import me.kuye.spider.entity.Answer;
import me.kuye.spider.entity.Question;
import me.kuye.spider.pipeline.impl.AnswerPipeline;
import me.kuye.spider.pipeline.impl.ConsolePipeline;
import me.kuye.spider.processor.Processor;
import me.kuye.spider.processor.helper.AnswerProcessorHelper;
import me.kuye.spider.processor.helper.QuestionProcessorHelper;
import me.kuye.spider.util.Constant;
import me.kuye.spider.util.HttpConstant;

/**
 * @author xianyijun
 *	抓取该问题所有回答
 */
public class AnswerProcessor implements Processor {
	private static final Logger logger = LoggerFactory.getLogger(AnswerProcessor.class);

	@Override
	public void process(Page page) {
		Request request = page.getRequest();
		String requestUrl = request.getUrl();
		Document doc = page.getDocument();
		if (requestUrl.startsWith(Constant.ZHIHU_ANSWER_URL)) {
			String urlToken = (String) request.getExtra(Constant.QUESTION_URL_TOKEN);
			AnswerResult answerResult = null;
			answerResult = JSONObject.parseObject(page.getRawtext(), AnswerResult.class);
			String[] msg = answerResult.getMsg();
			for (int i = 0; i < msg.length; i++) {
				Document answerDoc = Jsoup.parse(msg[i]);
				String relativeUrl = answerDoc.select("div.zm-item-answer link").attr("href");
				Answer answer = new Answer(relativeUrl);
				AnswerProcessorHelper.processAnswerDetail(answerDoc, answer);
				answer.setUrlToken(urlToken);
				page.getResult().add(answer);
			}
		} else {
			//解析问题详情请求
			Question question = new Question(requestUrl);
			QuestionProcessorHelper.processQuestion(page, question);
			String xsrf = doc.select("input[name=_xsrf]").attr("value");
			List<Request> answerList = AnswerProcessorHelper.processAnswerList(question.getUrlToken(), xsrf,
					question.getAnswerNum());
			page.getTargetRequest().addAll(answerList);
		}
	}

	public static void main(String[] args) {
		String url = "https://www.zhihu.com/question/20790679";
		if (args != null && args.length > 0) {
			url = args[0];
		}
		SimpleSpider.getInstance(new AnswerProcessor()).setThreadNum(3).setDomain("answer")
				.setScheduler(new AnswerRedisScheduler()).addPipeline(new ConsolePipeline())
				.addPipeline(new AnswerPipeline()).setStartRequest(new Request(HttpConstant.GET, url)).run();
	}
}
