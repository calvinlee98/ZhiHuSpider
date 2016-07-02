package me.kuye.spider.processor.sample;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import me.kuye.spider.ZhiHuSpider;
import me.kuye.spider.entity.Answer;
import me.kuye.spider.entity.Page;
import me.kuye.spider.entity.Question;
import me.kuye.spider.entity.Request;
import me.kuye.spider.pipeline.ConsolePipeline;
import me.kuye.spider.processor.Processor;
import me.kuye.spider.util.Constant;
import me.kuye.spider.util.HttpConstant;
import me.kuye.spider.vo.AnswerResult;
import me.kuye.spider.vo.UpVoteResult;
import me.kuye.spider.vo.UpVoteUser;

public class ZhiHuAnswerProcessor implements Processor {
	private static Logger logger = LoggerFactory.getLogger(ZhiHuAnswerProcessor.class);

	@Override
	public void process(Page page) {
		String requestUrl = page.getRequest().getUrl();
		Document doc = page.getDocument();
		//解析点赞用户列表请求
		if (requestUrl.indexOf("voters_profile") != -1) {
			List<UpVoteUser> upVoteUserList = processUpVoteUserList(page);
			page.getResult().addAll(upVoteUserList);
		}
		//解析问题列表请求
		else if (requestUrl.equals(Constant.ZHIHU_ANSWER_URL)) {
			AnswerResult answerResult = null;
			answerResult = JSONObject.parseObject(page.getRawtext(), AnswerResult.class);
			String[] msg = answerResult.getMsg();
			for (int i = 0; i < msg.length; i++) {
				Document answerDoc = Jsoup.parse(msg[i]);
				String relativeUrl = answerDoc.select("div.zm-item-answer link").attr("href");

				Answer answer = new Answer(relativeUrl, Constant.ZHIHU_URL + relativeUrl);
				processAnswerDetail(page, answerDoc, answer);
				page.getResult().add(answer);
			}
		} else if (requestUrl.matches("")) {

		}
		//解析问题详情请求
		else {
			Question question = new Question(requestUrl);
			processQuestion(page, question);
			page.getResult().add(question);

			String xsrf = doc.select("input[name=_xsrf]").attr("value");
			List<Request> answerList = processAnswerList(question.getUrlToken(), xsrf, question.getAnswerNum());
			page.getTargetRequest().addAll(answerList);
		}
	}

	/**
	* @Title: processQuestion
	* @Description: 解析问题详情
	* @param     参数
	* @return void    返回类型
	* @throws
	*/
	private static void processQuestion(Page page, Question question) {
		Document doc = page.getDocument();
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
	}

	/**
	* @Title: processAnswerList
	* @Description: 获取问题回答请求列表
	* @param     参数
	* @return List<Request>    返回类型
	* @throws
	*/
	private static List<Request> processAnswerList(String urlToken, String xsrf, long answerNum) {
		List<Request> answerRequestList = new ArrayList<>();
		for (int i = 0; i < answerNum / 10 + 1; i++) {
			HttpPost answerRequest = new HttpPost(Constant.ZHIHU_ANSWER_URL);
			List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();
			valuePairs.add(new BasicNameValuePair("method", "next"));
			valuePairs.add(new BasicNameValuePair("xsrf", xsrf));
			JSONObject obj = new JSONObject();
			obj.put("url_token", urlToken);
			// 并没有什么用，服务器端固定为10
			obj.put("pagesize", 10);
			obj.put("offset", 10 * i);
			valuePairs.add(new BasicNameValuePair("params", obj.toJSONString()));
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
			answerRequest.setHeader("Referer", "https://www.zhihu.com");
			answerRequest.setEntity(entity);

			answerRequestList
					.add(new Request(answerRequest.getMethod(), answerRequest.getURI().toString(), answerRequest)
							.addExtra(HttpConstant.NO_COOKIE, HttpConstant.NO_COOKIE));
		}
		return answerRequestList;
	}

	/**
	* @Title: processAnswerDetail
	* @Description: 解析回答详情，并添加点赞用户详情
	* @param     参数
	* @return void    返回类型
	* @throws
	*/
	private static void processAnswerDetail(Page page, Document answerDoc, Answer answer) {
		answer.setContent(answerDoc.select("div[data-entry-url=" + answer.getRelativeUrl() + "]  .zm-editable-content")
				.html().replaceAll("<br>", ""));

		answer.setUpvote(Long.parseLong(answerDoc.select(".zm-votebar span.count").first().text()));

		/*
		 * 不可以直接用a.author-link来获取，如果是知乎用户的话，不存在该标签 
		 * #zh-question-answer-wrap >div > div.answer-head > div.zm-item-answer-author-info >a.author-link 普通用户 2 
		 * #zh-question-answer-wrap > div > div.answer-head> div.zm-item-answer-author-info > span 知乎用户 2
		 * #zh-question-answer-wrap > div > div.answer-head >div.zm-item-answer-author-info > span 匿名用户 1
		 * 我们可以先通过获取zm-item-answer-author-info来获取用户名，因为是否为知乎用户的话
		 */
		Element authorInfo = answerDoc.select(".zm-item-answer-author-info").first();

		String author = authorInfo.select(".name").size() == 0 ? authorInfo.select("a.author-link").text()
				: authorInfo.select(".name").text();
		answer.setAuthor(author);

		String dataAid = answerDoc.select(".zm-item-answer").attr("data-aid");
		answer.setDataAid(dataAid);

		String upvoteUserUrl = "/answer/" + dataAid + "/voters_profile?&offset=0";
		HttpGet request = new HttpGet(Constant.ZHIHU_URL + upvoteUserUrl);
		page.getTargetRequest().add(new Request(request.getMethod(), request.getURI().toString(), request));
	}

	/**
	* @Title: processUpVoteUserList
	* @Description: 解析点赞用户列表信息，并添加next请求
	* @param     参数
	* @return List<UpVoteUser>    返回类型
	* @throws
	*/
	private static List<UpVoteUser> processUpVoteUserList(Page page) {
		UpVoteResult upVoteResult = null;
		List<UpVoteUser> userList = new LinkedList<>();
		upVoteResult = JSON.parseObject(page.getRawtext(), UpVoteResult.class);
		String[] payload = upVoteResult.getPayload();
		for (int i = 0; i < payload.length; i++) {
			Document doc = Jsoup.parse(payload[i]);
			UpVoteUser upVoteUser = new UpVoteUser();
			Element avatar = doc.select("img.zm-item-img-avatar").first();
			upVoteUser.setAvatar(avatar.attr("src"));
			//点赞用户不为匿名用户
			if (!"匿名用户".equals(avatar.attr("title"))) {
				upVoteUser.setName(doc.select(".zg-link").attr("title"));
				upVoteUser.setBio(doc.select(".bio").text());
				upVoteUser.setAgree(doc.select(".status").first().child(0).text());
				upVoteUser.setThanks(doc.select(".status").first().child(1).text());
				upVoteUser.setAnswers(doc.select(".status").first().child(3).text());
				upVoteUser.setAsks(doc.select(".status").first().child(2).text());
			} else {
				upVoteUser.setName(avatar.attr("title"));
			}
			userList.add(upVoteUser);
		}
		String nextUrl = upVoteResult.getPaging().getNext();
		if (!nextUrl.equals("") && nextUrl.trim().length() > 0) {
			HttpGet getMethod = new HttpGet(Constant.ZHIHU_URL + nextUrl);
			page.getTargetRequest().add(new Request(getMethod.getMethod(), getMethod.getURI().toString(), getMethod));
		}
		return userList;
	}

	public static void main(String[] args) {
		HttpGet getRequest = new HttpGet("https://www.zhihu.com/question/24430010");

		ZhiHuSpider.getInstance(new ZhiHuAnswerProcessor()).setThreadNum(3).setDomain("answer")
				.addPipeline(new ConsolePipeline())
				.setStartRequest(new Request(getRequest.getMethod(), getRequest.getURI().toString(), getRequest)).run();
	}
}
