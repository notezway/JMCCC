package org.to2mbn.jmccc.mcdownloader.provider;

import java.io.File;
import java.net.URI;
import org.to2mbn.jmccc.mcdownloader.download.DownloadTask;
import org.to2mbn.jmccc.version.Library;

/**
 * Creates a download task for a library.
 * <p>
 * Each <code>LibraryDownloadHandler</code> handles one kind of libraries. For example, JarLibraryDownloadHandler
 * handles the libraries ending with '.jar', PackLibraryDownloadHandler handles the libraries ending with '.jar.pack',
 * XZPackLibraryDownloadHandler handles the libraries ending with '.jar.pack.xz'.
 * 
 * @author yushijinhun
 */
public interface LibraryDownloadHandler {

	DownloadTask<Object> createDownloadTask(File target, Library library, URI libraryUri);

}
