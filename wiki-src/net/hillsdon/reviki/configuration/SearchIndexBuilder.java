package net.hillsdon.reviki.configuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.servlet.ServletContext;

import net.hillsdon.reviki.search.impl.ExternalCommitAwareSearchEngine;
import net.hillsdon.reviki.search.impl.LuceneSearcher;
import net.hillsdon.reviki.vc.PageInfo;
import net.hillsdon.reviki.vc.VersionedPageInfo;
import net.hillsdon.reviki.vc.PageReference;
import net.hillsdon.reviki.vc.PageStoreAuthenticationException;
import net.hillsdon.reviki.vc.PageStoreException;
import net.hillsdon.reviki.vc.impl.ConfigPageCachingPageStore;
import net.hillsdon.reviki.vc.impl.InMemoryDeletedRevisionTracker;
import net.hillsdon.reviki.vc.impl.RepositoryBasicSVNOperations;
import net.hillsdon.reviki.vc.impl.SVNPageStore;
import net.hillsdon.reviki.web.urls.InternalLinker;
import net.hillsdon.reviki.web.urls.URLOutputFilter;
import net.hillsdon.reviki.web.urls.WikiUrls;
import net.hillsdon.reviki.web.urls.impl.ApplicationUrlsImpl;
import net.hillsdon.reviki.web.urls.impl.PageStoreConfiguration;
import net.hillsdon.reviki.web.vcintegration.AutoProperiesFromConfigPage;
import net.hillsdon.reviki.wiki.MarkupRenderer;
import net.hillsdon.reviki.wiki.graph.WikiGraph;
import net.hillsdon.reviki.wiki.graph.WikiGraphImpl;
import net.hillsdon.reviki.wiki.macros.AttrMacro;
import net.hillsdon.reviki.wiki.macros.IncomingLinksMacro;
import net.hillsdon.reviki.wiki.macros.OutgoingLinksMacro;
import net.hillsdon.reviki.wiki.macros.SearchMacro;
import net.hillsdon.reviki.wiki.plugin.Plugins;
import net.hillsdon.reviki.wiki.plugin.PluginsImpl;
import net.hillsdon.reviki.wiki.renderer.SvnWikiRenderer;
import net.hillsdon.reviki.wiki.renderer.creole.ast.ASTNode;
import net.hillsdon.reviki.wiki.renderer.macro.Macro;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import com.google.common.base.Supplier;

public class SearchIndexBuilder implements Runnable {
  private static final Log LOG = LogFactory.getLog(SearchIndexBuilder.class);

  private SvnWikiRenderer _renderer;
  private ExternalCommitAwareSearchEngine _searchEngine;
  private Plugins _plugins;
  private Thread _thread;
  private boolean _shuttingDown;
  private final DeploymentConfiguration _configuration;

  public SearchIndexBuilder(ServletContext servletContext) {
    DataDir dataDir = new DataDirImpl(servletContext);
    _configuration = new PropertiesDeploymentConfiguration(dataDir);
    // build the search index for every wiki
  }

  public SearchIndexBuilder(DeploymentConfiguration configuration) {
    _configuration = configuration;
  }

  public void start() {
    _shuttingDown = false;
    _thread = new Thread(this);
    _thread.start();
  }

  public void stop() {
    _shuttingDown = true;
  }

  public void run() {
    _configuration.load();
    for (WikiConfiguration wikiConf: _configuration.getWikis()) {
      if(_shuttingDown) return;
      try {
        indexWiki(wikiConf);
      }
      catch(PageStoreAuthenticationException e) {
        // writeUnsuccessful was already done
        e.printStackTrace();
      }
    }
  }

  public void indexWiki(WikiConfiguration wikiConf) throws PageStoreAuthenticationException {
    String wikiName = wikiConf.getWikiName();
    File primarySearchDir = wikiConf.getSearchIndexDirectory();
    List<File> otherSearchDirs = wikiConf.getOtherSearchIndexDirectories();
    // The wrapping MarkupRenderer contortion is necessary because we haven't initialised _renderer yet.
    MarkupRenderer<String> renderer = new MarkupRenderer<String>() {
      @Override
      public ASTNode parse(PageInfo page) throws IOException, PageStoreException {
        return _renderer.parse(page);
      }

      @Override
      public String render(ASTNode ast, URLOutputFilter urlOutputFilter) throws IOException, PageStoreException {
        return _renderer.render(ast, urlOutputFilter);
      }
    };
    LuceneSearcher searcher = new LuceneSearcher(wikiConf.getWikiName(), primarySearchDir, otherSearchDirs, renderer);
    _searchEngine = new ExternalCommitAwareSearchEngine(searcher);
    long latestRevision = -1;
    try {
      DAVRepositoryFactory.setup();
      SVNRepository repository = SVNRepositoryFactory.create(wikiConf.getUrl());
      String user = wikiConf.getSVNUser();
      String password = wikiConf.getSVNPassword();
      repository.setAuthenticationManager(new BasicAuthenticationManager(user, password));
      RepositoryBasicSVNOperations operations = new RepositoryBasicSVNOperations(repository, null);
      final SVNPageStore store = new SVNPageStore(wikiName, new InMemoryDeletedRevisionTracker(), operations, null, null);
      _plugins = new PluginsImpl(store);
      _searchEngine.setPageStore(store);
      ConfigPageCachingPageStore cachingPageStore = new ConfigPageCachingPageStore(store);
      AutoProperiesFromConfigPage autoProperties = new AutoProperiesFromConfigPage();
      autoProperties.setPageStore(cachingPageStore);
      String base = wikiConf.getFixedBaseUrl();
      ApplicationUrlsImpl urls = new ApplicationUrlsImpl(base, _configuration);
      WikiUrls wikiUrls = urls.get(wikiName);
      InternalLinker internalLinker = new InternalLinker(wikiUrls);
      final WikiGraph wikiGraph = new WikiGraphImpl(cachingPageStore, _searchEngine);
      _renderer = new SvnWikiRenderer(new PageStoreConfiguration(cachingPageStore, urls), store, internalLinker, new Supplier<List<Macro>>() {
        public List<Macro> get() {
          List<Macro> macros = new ArrayList<Macro>(Arrays.<Macro>asList(new IncomingLinksMacro(wikiGraph), new OutgoingLinksMacro(wikiGraph), new SearchMacro(_searchEngine), new AttrMacro(store)));
          macros.addAll(_plugins.getImplementations(Macro.class));
          return macros;
        }
      });

      latestRevision = operations.getLatestRevision();
      long latestIndexed = searcher.getHighestIndexedRevision();
      LOG.debug(wikiName + " revision " + latestRevision + " (latest indexed " + latestIndexed + ")");
      // if latest indexed == 0 shortcut to do latest rev of each page
      // also seen latest indexed == -1
      // or if the number of revs to index is greater than the number of pages??
      // Would need to base the second one on the filtered log, the rev numbers would include far too much that's outside the wiki.
      if (latestIndexed < latestRevision) {
        long start = System.currentTimeMillis();
        searcher.setIndexBeingBuilt(true);
        final Collection<PageReference> minimized = store.getChangedBetween(latestIndexed + 1, latestRevision);

        for (PageReference page : minimized) {
          try {
            if(_shuttingDown) return;
            VersionedPageInfo info = store.get(page, -1);
            if (info.isNewPage()) {
              searcher.delete(info.getWiki(), info.getPath(), true);
            }
            else {
              searcher.index(info, true);
            }
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
        LOG.debug("indexed in " + (System.currentTimeMillis() - start) + "ms");
      }
    }

    catch (PageStoreAuthenticationException e) {
      writeUnsuccessful(searcher);
      // throw the exception, so we know it's because of authentication
      throw e;
    }
    catch (SVNException e) {
      e.printStackTrace();
    }
    catch (PageStoreException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    finally {
      writeIndexedRevision(searcher, latestRevision);
    }
  }

  private void writeUnsuccessful(LuceneSearcher searcher) {
    writeIndexedRevision(searcher, -1L);
  }

  private void writeIndexedRevision(LuceneSearcher searcher, long latestRevision) {
    try {
      searcher.setIndexBeingBuilt(false);
      searcher.rememberHighestIndexedRevision(latestRevision);
    } catch(IOException ex) {
      ex.printStackTrace();
    }
  }
}
