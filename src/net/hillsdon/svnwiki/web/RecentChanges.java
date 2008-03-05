package net.hillsdon.svnwiki.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.hillsdon.svnwiki.vc.PageStore;
import net.hillsdon.svnwiki.vc.PageStoreException;
import net.hillsdon.svnwiki.vc.PageStoreFactory;

public class RecentChanges extends PageRequestHandler {

  public RecentChanges(final PageStoreFactory pageStoreFactory) {
    super(pageStoreFactory);
  }

  public void handlePage(final HttpServletRequest request, final HttpServletResponse response, final PageStore store, final String page) throws PageStoreException, IOException, ServletException {
    request.setAttribute("pageList", store.recentChanges());
    request.getRequestDispatcher("/WEB-INF/templates/PageList.jsp").forward(request, response);
  }

}