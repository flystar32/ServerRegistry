package com.xiaojing.registry.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiaojing on 16/6/15.
 */
public class URLGroup {

  private List<URL> urls = new ArrayList<>();

  public List<URL> getUrls() {
    return urls;
  }

  public void setUrls(List<URL> urls) {
    this.urls = urls;
  }

  public void addUrl(URL url) {
    urls.add(url);
  }

}
