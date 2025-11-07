package com.acme.reliable.processor.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

@Factory
@Requires(property = "redisson.enabled", value = "true", defaultValue = "true")
public class RedissonFactory {

  @Singleton
  @Requires(property = "redisson.address")
  public RedissonClient redissonClient(@Property(name = "redisson.address") String address) {
    Config config = new Config();
    config.useSingleServer().setAddress(address);
    return Redisson.create(config);
  }
}
