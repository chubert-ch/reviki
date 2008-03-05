package net.hillsdon.svnwiki.web.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.hillsdon.svnwiki.search.SearchEngine;
import net.hillsdon.svnwiki.vc.ConfigPageCachingPageStore;
import net.hillsdon.svnwiki.vc.PageReference;
import net.hillsdon.svnwiki.web.ConsumedPath;
import net.hillsdon.svnwiki.wiki.MarkupRenderer;

public class RegularPage extends PageRequestHandler {

  private final PageRequestHandler _viewRegularPage;
  private final PageRequestHandler _editor;
  private final PageRequestHandler _set;
  private final PageRequestHandler _history;

  public RegularPage(final ConfigPageCachingPageStore cachingPageStore, final MarkupRenderer markupRenderer, final SearchEngine searchEngine) {
    super(cachingPageStore);
    _viewRegularPage = new GetRegularPage(cachingPageStore, markupRenderer, searchEngine);
    // It is important to use the non-caching page store here.  It is ok to view 
    // something out of date but users must edit the latest revision or else they
    // won't be able to commit.
    _editor = new EditorForPage(cachingPageStore.getUnderlying());
    _set = new SetPage(cachingPageStore);
    _history = new History(cachingPageStore);
  }
  
  @Override
  public void handlePage(final ConsumedPath path, final HttpServletRequest request, final HttpServletResponse response, final PageReference page) throws Exception {
    if (request.getParameter("history") != null) {
      _history.handlePage(path, request, response, page);
    }
    else if ("POST".equals(request.getMethod())) {
      if (request.getParameter(SetPage.PARAM_CONTENT) != null) {
        _set.handlePage(path, request, response, page);
      }
      else {
        _editor.handlePage(path, request, response, page);
      }
    }
    else {
      _viewRegularPage.handlePage(path, request, response, page);
    }
  }

}