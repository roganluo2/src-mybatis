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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";
//  用于判断循环终止条件，foreachsqlnode构造方法中会创建该对象、
  private final ExpressionEvaluator evaluator;
//  迭代的集合表达式
  private final String collectionExpression;
//  记录了该foreachsqlnode节点的子节点
  private final SqlNode contents;
//  在循环开始前要添加的字符串
  private final String open;
//  在循环结束后要添加的字符串
  private final String close;
//  分隔符
  private final String separator;
//  index是迭代次数，item是元素，如果是map，index是键，item是值
  private final String item;
  private final String index;
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
//    获取参数信息
    Map<String, Object> bindings = context.getBindings();
//    解析集合表达式对应的实际参数
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
//    检查集合长度
    if (!iterable.iterator().hasNext()) {
      return true;
    }
//    步骤2，在循环开始前，调用dynamiccontext.appendSql方法添加open指定的字符串
    boolean first = true;
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
//      记录当前的dynamicContext对象、
      DynamicContext oldContext = context;
//      步骤3创建prefixedContext,并让context指向该prefixcontext对象
      if (first || separator == null) {
//        如果是集合的第一项，则将prefixedContext.prefix初始化为空字符串
        context = new PrefixedContext(context, "");
      } else {
//        如果指定了分隔符，则prefixedContext.prefix初始化为指定分隔符
        context = new PrefixedContext(context, separator);
      }
//      uniqueNumber从0开始，每次递增1，用于转换生成的#{}占位符名称
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709 
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
//                如果集合是map类型，将集合中key和value添加到dynamiccontext.bindings集合中保存
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
//        步骤4
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
//        步骤5
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
//        将集合中的索引和元素添加到dynamiccontext.bindings集合中、
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
//      步骤6，调用子节点的apply方法，进行处理，注意这里使用的是filteredDynamicContext对象
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
//      还原原来的context
      context = oldContext;
      i++;
    }
//    结束循环后，调用dynamiccontext。appendsql方法添加到close指定的字符串、
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }
//  applyIndex方法以及后面介绍的applyitem方法的第三个参数i,该值由dynamiccontext产生，每个dynamiccontext对象的生命周期中是单调递增
  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
//      key为index value是集合元素
      context.bind(index, o);
//      为index添加前后缀形成新的key
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
//      key为item value 为集合项
      context.bind(item, o);
//      为item添加前缀和后缀形成新的key
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
//    添加“_frc_前缀和i后缀
    return new StringBuilder(ITEM_PREFIX).append(item).append("_").append(i).toString();
  }

  private static class FilteredDynamicContext extends DynamicContext {
//    dynamicContext
    private final DynamicContext delegate;
//    对应集合项在集合中的索引位置、
    private final int index;
//    对应集合项的index，参见foreachsqlnode.index
    private final String itemIndex;
//    对应集合的item
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
//      创建genericTokenParser解析器，注意这里是匿名实现的tokenHandler对象、
      GenericTokenParser parser = new GenericTokenParser("#{", "}", new TokenHandler() {
        @Override
        public String handleToken(String content) {
//          对item进行处理
          String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
          if (itemIndex != null && newContent.equals(content)) {
//            对itemindex进行处理
            newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
          }
          return new StringBuilder("#{").append(newContent).append("}").toString();
        }
      });
//      将解析后的sql语句片段追加到delegate中保存
      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  private class PrefixedContext extends DynamicContext {
//    底层封装dynamiccontext对象
    private final DynamicContext delegate;
//    指定的前缀
    private final String prefix;
//    是否已经处理过前缀
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
//      判断是否需要追加前缀
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
//        追加前缀
        delegate.appendSql(prefix);
//        表示已经处理过前缀
        prefixApplied = true;
      }
//      追加sql片段
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
