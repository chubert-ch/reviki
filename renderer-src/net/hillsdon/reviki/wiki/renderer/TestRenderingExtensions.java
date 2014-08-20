/**
 * Copyright 2008 Matthew Hillsdon
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
package net.hillsdon.reviki.wiki.renderer;

import java.io.IOException;

import net.hillsdon.reviki.vc.PageStoreException;
import net.hillsdon.reviki.wiki.renderer.creole.JsonDrivenRenderingTest;

import org.codehaus.jackson.JsonParseException;

public class TestRenderingExtensions extends JsonDrivenRenderingTest {

  public TestRenderingExtensions() throws JsonParseException, IOException {
    super(TestRenderingExtensions.class.getResource("rendering-extensions.json"));
  }

  @Override
  protected String render(final String input) throws IOException, PageStoreException {
    HtmlRenderer renderer = new HtmlRenderer(pageStore, linkHandler, imageHandler, macros);
    return renderer.render(input).get();
  }
}
