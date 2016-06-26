package me.kuye.spider.entity;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class Question implements Serializable{

	private static final long serialVersionUID = -109150094039975443L;

	private String url;// 绝对路径
	private String title;
	private String description;
	private long answerNum;
	private long answerFollowersNum;
	private long visitTimes;
	private String[] topics;
	private List<Answer> allAnswerList;

	public Question() {

	}

	public Question(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getAnswerNum() {
		return answerNum;
	}

	public void setAnswerNum(long answerNum) {
		this.answerNum = answerNum;
	}

	public long getAnswerFollowersNum() {
		return answerFollowersNum;
	}

	public void setAnswerFollowersNum(long answerFollowersNum) {
		this.answerFollowersNum = answerFollowersNum;
	}

	public String[] getTopics() {
		return topics;
	}

	public void setTopics(String[] topics) {
		this.topics = topics;
	}

	public long getVisitTimes() {
		return visitTimes;
	}

	public void setVisitTimes(long visitTimes) {
		this.visitTimes = visitTimes;
	}

	public List<Answer> getAllAnswerList() {
		return allAnswerList;
	}

	public void setAllAnswerList(List<Answer> allAnswerList) {
		this.allAnswerList = allAnswerList;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((allAnswerList == null) ? 0 : allAnswerList.hashCode());
		result = prime * result + (int) (answerFollowersNum ^ (answerFollowersNum >>> 32));
		result = prime * result + (int) (answerNum ^ (answerNum >>> 32));
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
		result = prime * result + Arrays.hashCode(topics);
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		result = prime * result + (int) (visitTimes ^ (visitTimes >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Question other = (Question) obj;
		if (allAnswerList == null) {
			if (other.allAnswerList != null)
				return false;
		} else if (!allAnswerList.equals(other.allAnswerList))
			return false;
		if (answerFollowersNum != other.answerFollowersNum)
			return false;
		if (answerNum != other.answerNum)
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		if (!Arrays.equals(topics, other.topics))
			return false;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		if (visitTimes != other.visitTimes)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Question [url=" + url + ", title=" + title + ", description=" + description + ", answerNum=" + answerNum
				+ ", answerFollowersNum=" + answerFollowersNum + ", visitTimes=" + visitTimes + ", topics="
				+ Arrays.toString(topics) + ", allAnswerList=" + allAnswerList + "]";
	}
	
}