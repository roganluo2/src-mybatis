/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;
//  类型对象，它是一个有序的hashmap，用于记录key最近使用情况
  private Map<Object, Object> keyMap;
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
//    重新设置缓存大小，会重置keymap字段
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
//      注意第三个参数是true,表示该linkedhashmap记录的顺序是access-order,也就是说，linkedhashmap.get()方法会改变其记录顺序
      private static final long serialVersionUID = 4267176411845948333L;

//      当调用linkedhashmap.put方法，会调用该方法
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
//          如果已经达到缓存上线，则更新eldestkey字段，后面会删除该项
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
//    修改linkedhashmap中记录的顺序
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
//    添加缓存项
    delegate.clear();
//    删除最久未使用的缓存项
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
//    eldestkey不为空，表示已经达到最大缓存上限
    if (eldestKey != null) {
//      删除最久没有使用的缓存项
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
