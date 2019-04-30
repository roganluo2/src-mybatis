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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class TrimSqlNode implements SqlNode {
//  该trim节点的子节点
  private final SqlNode contents;
//  记录了前缀字符串(<trim节点包裹的sql语句添加的前缀
  private final String prefix;
//  记录后后缀
  private final String suffix;
//  如果trim节点包裹的sql语句是空（经常出现在if庞端为否的情况下）删除指定的的前缀，如where
  private final List<String> prefixesToOverride;
//  trim语句为空，删除后缀，如逗号、
  private final List<String> suffixesToOverride;
  private final Configuration configuration;

  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
//    创建filteredDynamicContext对象，其中封装了dynamiccontext
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
//    调用子节点的apply方法进行解析
    boolean result = contents.apply(filteredDynamicContext);
//    使用filtereddynamiccontext.applyAll方法处理前缀和后缀
    filteredDynamicContext.applyAll();
    return result;
  }

  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
//      按照“|”进行分割
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      final List<String> list = new ArrayList<String>(parser.countTokens());
//      装换成大写，并添加到集合中
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }

  private class FilteredDynamicContext extends DynamicContext {
//    封装的dynamiccontext对象
    private DynamicContext delegate;
//    是否已经处理过前缀后缀，初始值都为false
    private boolean prefixApplied;
    private boolean suffixApplied;
//    用于记录子节点解析后的结果，filteredDynamicContext.appendSql()方法会向该字段添加解析结果，
//    而不是调用deletegate.appendSql方法
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
//      获取子节点解析后的结果，并全部转换为答谢
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      if (trimmedUppercaseSql.length() > 0) {
//        处理前缀
        applyPrefix(sqlBuffer, trimmedUppercaseSql);
//        处理后缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);
      }
//      将解析后的结果添加到delegate中
      delegate.appendSql(sqlBuffer.toString());
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
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
//      检测是否已经处理过前缀
      if (!prefixApplied) {
//        标记已处理过前缀
        prefixApplied = true;
        if (prefixesToOverride != null) {
//          遍历prefixesToOveride集合,如果是以prefixedToOveride中某项开头，则将该项从sql语句开头删除掉
          for (String toRemove : prefixesToOverride) {
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
//        添加prefix前缀
        if (prefix != null) {
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
//      检测是否已经处理过后缀
      if (!suffixApplied) {
//        表示已经处理过后缀
        suffixApplied = true;
        if (suffixesToOverride != null) {
//          遍历prefixesToOveride集合，如果以suffixestooveride中某项结果，则将该项从sql语句结尾删除掉
          for (String toRemove : suffixesToOverride) {
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
//        添加suffix后缀
        if (suffix != null) {
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
