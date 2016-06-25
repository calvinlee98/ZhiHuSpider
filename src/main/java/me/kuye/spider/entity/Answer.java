package me.kuye.spider.entity;

import java.util.List;

public class Answer {
	private String absUrl;
	private String relativeUrl;
	private Question question;
	private String author;
	private long upvote;
	private String content;
	
	public Answer(String relativeUrl, String absUrl) {
		this.relativeUrl = relativeUrl;
		this.absUrl = absUrl;
	}

	public String getAbsUrl() {
		return absUrl;
	}

	public void setAbsUrl(String absUrl) {
		this.absUrl = absUrl;
	}

	public String getRelativeUrl() {
		return relativeUrl;
	}

	public void setRelativeUrl(String relativeUrl) {
		this.relativeUrl = relativeUrl;
	}

	/*
	 * https://www.zhihu.com/answer/38441951/voters_profile?&offset=10
	 * 根据answer的data-aid获取点赞用户列表，然后根据返回的json数据的next是否为空判断 此处存储点赞用户的url地址
	 * 
	 */
	private List<String> upvoteUserList;// 点赞用户列表

	public Question getQuestion() {
		return question;
	}

	public void setQuestion(Question question) {
		this.question = question;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public long getUpvote() {
		return upvote;
	}

	public void setUpvote(long upvote) {
		this.upvote = upvote;
	}

	public List<String> getUpvoteUserList() {
		return upvoteUserList;
	}

	public void setUpvoteUserList(List<String> upvoteUserList) {
		this.upvoteUserList = upvoteUserList;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

}
