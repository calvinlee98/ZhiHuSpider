package me.kuye.spider.dao.redis;

import me.kuye.spider.entity.UrlItem;

public class UrlItemDao extends RedisBaseDao<UrlItem> {
	public boolean exist(String url) {
		return redisManager.sismember("url", url);
	}

	public boolean add(String url) {
		return redisManager.sadd("url", url) != 0;
	}
}
