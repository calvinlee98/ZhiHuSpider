package me.kuye.spider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import me.kuye.spider.downloader.HttpDownloader;
import me.kuye.spider.entity.Answer;
import me.kuye.spider.entity.Question;
import me.kuye.spider.entity.UpVoteUser;
import me.kuye.spider.manager.MongoManager;
import me.kuye.spider.util.Constant;
import me.kuye.spider.util.HttpConstant;
import me.kuye.spider.util.MongoUtil;
import me.kuye.spider.vo.AnswerResult;
import me.kuye.spider.vo.UpVoteResult;

/**
 * @author xianyijun
 *
 */
public class QuestionSpider {
	private static Logger logger = LoggerFactory.getLogger(QuestionSpider.class);
	private static final HttpDownloader downloader = new HttpDownloader();
	private static CloseableHttpClient client = downloader.getHttpClient("question");

	public static void main(String[] args) throws IOException {

		String url = "https://www.zhihu.com/question/47809575";

		if (args != null && args.length > 0) {
			url = args[0];
		}

		HttpGet request = new HttpGet(url);
		CloseableHttpResponse response = null;
		Question question = new Question(url);
		try {
			response = client.execute(request);
			String body = EntityUtils.toString(response.getEntity());

			Document doc = Jsoup.parse(body, Constant.ZHIHU_URL);

			processQuestion(doc, question);

			String xsrf = doc.select("input[name=_xsrf]").attr("value");

			List<Answer> answerList = processAnswerList(question.getUrlToken(), xsrf, question.getAnswerNum());

			logger.info(" question url : " + question.getUrl());
			logger.info(" title : " + question.getTitle());
			logger.info(" description : " + question.getDescription());
			logger.info(" followerNum : " + question.getAnswerFollowersNum());
			logger.info(" answerNum : " + question.getAnswerNum());
			logger.info(" visitTimes : " + question.getVisitTimes());
			for (int i = 0; i < answerList.size(); i++) {
				Answer answer = answerList.get(i);
				logger.info(" author : " + answer.getAuthor());
				logger.info(" upvote : " + answer.getUpvote());
				logger.info(" content : " + answer.getContent());
			}
			// MongoManager.getInstance().insertOne("question",
			// MongoUtil.objectToDocument(Question.class, question));
			doc.select("#zh-question-related-questions ul li a").forEach((Element e) -> {
				System.out.println(e.attr("abs:href"));
			});
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			request.abort();
			if (response != null) {
				EntityUtils.consumeQuietly(response.getEntity());
			}
		}
	}

	private static List<Answer> processAnswerList(String urlToken, String xsrf, long answerNum) {
		// 更换client，key为null的client没有cookie，防止用户访问频率被封
		client = downloader.getHttpClient(HttpConstant.NO_COOKIE);

		logger.info(" urlToken : " + urlToken + " answerNum: " + answerNum);

		HttpPost answerRequest = new HttpPost(Constant.ZHIHU_ANSWER_URL);

		CloseableHttpResponse response = null;
		List<Answer> answerList = new ArrayList<>();

		List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();
		valuePairs.add(new BasicNameValuePair("method", "next"));
		valuePairs.add(new BasicNameValuePair("xsrf", xsrf));
		JSONObject obj = new JSONObject();
		obj.put("url_token", urlToken);
		// 并没有什么用，服务器端固定为10
		obj.put("pagesize", 10);

		for (int i = 0; i < answerNum / 10 + 1; i++) {
			obj.put("offset", 10 * i);
			valuePairs.add(new BasicNameValuePair("params", obj.toJSONString()));

			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
			answerRequest.setHeader("Referer", "https://www.zhihu.com");
			answerRequest.setEntity(entity);

			try {
				response = client.execute(answerRequest);
				String result = EntityUtils.toString(response.getEntity());
				logger.info(result);
				AnswerResult answerResult = null;
				answerResult = JSONObject.parseObject(result, AnswerResult.class);

				String[] msg = answerResult.getMsg();

				for (int j = 0; j < msg.length; j++) {
					Document answerDoc = Jsoup.parse(msg[j]);
					String relativeUrl = answerDoc.select("div.zm-item-answer link").attr("href");

					Answer answer = new Answer(relativeUrl, Constant.ZHIHU_URL + relativeUrl);
					processAnswerDetail(answer);

					answerList.add(answer);
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		return answerList;
	}

	private static void processAnswerDetail(Answer answer) throws IOException {

		logger.info(" answer absUrl: " + answer.getAbsUrl());

		Document answerDoc = Jsoup.connect(answer.getAbsUrl()).get();

		answer.setContent(answerDoc.select("div[data-entry-url=" + answer.getRelativeUrl() + "]  .zm-editable-content")
				.html().replaceAll("<br>", ""));

		answer.setUpvote(answerDoc.select(".zm-votebar span.count").first().text());

		/*
		 * 不可以直接用a.author-link来获取，如果是知乎用户的话，不存在该标签 #zh-question-answer-wrap >
		 * div > div.answer-head > div.zm-item-answer-author-info >
		 * a.author-link 普通用户 2 #zh-question-answer-wrap > div > div.answer-head
		 * > div.zm-item-answer-author-info > span 知乎用户 2
		 * #zh-question-answer-wrap > div > div.answer-head >
		 * div.zm-item-answer-author-info > span 匿名用户 1
		 * 我们可以先通过获取zm-item-answer-author-info来获取用户名，因为是否为知乎用户的话
		 */
		Element authorInfo = answerDoc.select(".zm-item-answer-author-info").first();

		String author = authorInfo.select(".name").size() == 0 ? authorInfo.select("a.author-link").text()
				: authorInfo.select(".name").text();
		answer.setAuthor(author);

		String dataAid = answerDoc.select(".zm-item-answer").attr("data-aid");
		answer.setDataAid(dataAid);

		String upvoteUserUrl = "/answer/" + dataAid + "/voters_profile?&offset=0";
		List<UpVoteUser> userList = processUpVoteUserList(upvoteUserUrl);
		answer.setUpvoteUserList(userList);
	}

	private static List<UpVoteUser> processUpVoteUserList(String upvoteUserUrl) {
		HttpGet request = null;
		CloseableHttpResponse response = null;
		UpVoteResult upVoteResult = null;
		String result = "";
		List<UpVoteUser> userList = new LinkedList<>();
		try {
			while (upvoteUserUrl != null && !upvoteUserUrl.equals("")) {
				request = new HttpGet(Constant.ZHIHU_URL + upvoteUserUrl);
				logger.info(" upVoteUser request url " + request.getURI().toString());
				response = client.execute(request);
				result = EntityUtils.toString(response.getEntity());
				upVoteResult = JSON.parseObject(result, UpVoteResult.class);
				String[] payload = upVoteResult.getPayload();
				for (int i = 0; i < payload.length; i++) {
					Document doc = Jsoup.parse(payload[i]);
					UpVoteUser upVoteUser = new UpVoteUser();
					upVoteUser.setName(doc.select(".zg-link").attr("title"));
					upVoteUser.setAvatar(doc.select("img.zm-item-img-avatar").attr("src"));
					upVoteUser.setBio(doc.select(".bio").text());
					upVoteUser.setAgree(doc.select(".status").first().child(0).text());
					upVoteUser.setThanks(doc.select(".status").first().child(1).text());
					upVoteUser.setAnswers(doc.select(".status").first().child(3).text());
					upVoteUser.setAsks(doc.select(".status").first().child(2).text());
					userList.add(upVoteUser);
				}
				upvoteUserUrl = upVoteResult.getPaging().getNext();
			}
		} catch (IOException e) {
			e.printStackTrace();
			userList = Collections.emptyList();
			logger.info("processAnswerDetail failure");
		}
		return userList;
	}

	private static void processQuestion(Document doc, Question question) {
		String urlToken = doc.select("#zh-single-question-page").attr("data-urltoken");
		question.setUrlToken(urlToken);

		String title = doc.select("#zh-question-title  h2  span").first().text();
		question.setTitle(title);

		String description = doc.select("#zh-question-detail div").first().text();
		question.setDescription(description);

		int answerNum = 0;
		try {
			answerNum = Integer.parseInt(doc.select("#zh-question-answer-num").attr("data-num"));
		} catch (NumberFormatException e) {
			//当问题回答数目小于1的时候，#zh-question-answer-num元素不存在。
			answerNum = doc.select(".zm-item-answer").size();
		}
		question.setAnswerNum(answerNum);

		// 只有登录才存在
		int visitTimes = Integer.parseInt(doc.select("div.zg-gray-normal strong").eq(1).text());
		question.setVisitTimes(visitTimes);

		int answerFollowersNum = Integer.parseInt(doc.select("div.zh-question-followers-sidebar strong").text());
		question.setAnswerFollowersNum(answerFollowersNum);

		Elements topicElements = doc.select(".zm-tag-editor-labels a");
		List<String> topics = new LinkedList<>();
		for (int i = 0; i < topicElements.size(); i++) {
			topics.add(topicElements.get(i).text());
		}
		question.setTopics(topics);

		logger.info(question.toString());
	}
}
