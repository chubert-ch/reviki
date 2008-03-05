/**
 * Copyright 2007 Matthew Hillsdon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hillsdon.svnwiki.wiki.renderer.creole;

import java.util.regex.Matcher;

import net.hillsdon.svnwiki.vc.PageReference;

public class LinkNode extends AbstractRegexNode {

  private final LinkContentSplitter _parser;
  private final LinkPartsHandler _handler;

  public LinkNode(final String regex, final LinkContentSplitter parser, final LinkPartsHandler handler) {
    super(regex);
    _parser = parser;
    _handler = handler;
  }

  public final String handle(final PageReference page, final Matcher matcher) {
    return _handler.handle(page, this, _parser.split(matcher));
  }

}