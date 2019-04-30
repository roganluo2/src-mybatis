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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class DefaultReflectorFactory implements ReflectorFactory {
//  该字段决定是否开启对reflector对象的缓存
  private boolean classCacheEnabled = true;
//  使用concurrentmap集合实现对reflector对象的缓存
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<Class<?>, Reflector>();

  public DefaultReflectorFactory() {
  }

  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  @Override
  public Reflector findForClass(Class<?> type) {
//    是否开启缓存
    if (classCacheEnabled) {
            // synchronized (type) removed see issue #461
      Reflector cached = reflectorMap.get(type);
      if (cached == null) {
//        创建reflector对象
        cached = new Reflector(type);
//        放入concurrentmap中缓存
        reflectorMap.put(type, cached);
      }
      return cached;
    } else {
//      未开启缓存，则直接创建并返回reflector对象
      return new Reflector(type);
    }
  }

}
