package org.to2mbn.jmccc.mcdownloader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.to2mbn.jmccc.mcdownloader.download.DownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.DownloadTask;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadContext;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadTask;
import org.to2mbn.jmccc.mcdownloader.provider.MinecraftDownloadProvider;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.version.Asset;
import org.to2mbn.jmccc.version.Library;
import org.to2mbn.jmccc.version.Version;
import org.to2mbn.jmccc.version.Versions;

public class IncrementallyDownloadTask extends CombinedDownloadTask<Version> {

	private MinecraftDirectory mcdir;
	private String version;
	private MinecraftDownloadProvider downloadProvider;
	private Set<String> handledVersions = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	public IncrementallyDownloadTask(MinecraftDownloadProvider downloadProvider, MinecraftDirectory mcdir, String version) {
		Objects.requireNonNull(mcdir);
		Objects.requireNonNull(version);
		Objects.requireNonNull(downloadProvider);
		this.mcdir = mcdir;
		this.version = version;
		this.downloadProvider = downloadProvider;
	}

	@Override
	public void execute(final CombinedDownloadContext<Version> context) throws Exception {
		handleVersionJson(version, context, new Callable<Object>() {

			@Override
			public Object call() throws Exception {
				final Version ver = Versions.resolveVersion(mcdir, version);
				if (!mcdir.getVersionJar(version).exists()) {
					context.submit(downloadProvider.gameJar(mcdir, ver), null, true);
				}
				for (Library library : ver.getMissingLibraries(mcdir)) {
					context.submit(downloadProvider.library(mcdir, library), null, true);
				}
				if (mcdir.getAssetIndex(ver.getAssets()).exists()) {
					downloadAssets(context, Versions.resolveAssets(mcdir, ver.getAssets()));
				} else {
					context.submit(downloadProvider.assetsIndex(mcdir, ver), new CombinedDownloadCallback<Set<Asset>>() {

						@Override
						public void done(final Set<Asset> result) {
							try {
								context.submit(new Callable<Object>() {

									@Override
									public Object call() throws Exception {
										downloadAssets(context, result);
										return null;
									}
								}, null, true);
							} catch (InterruptedException e) {
								context.cancelled();
							}
						}

						@Override
						public void failed(Throwable e) {
						}

						@Override
						public void cancelled() {
						}

						@Override
						public <R> DownloadCallback<R> taskStart(DownloadTask<R> task) {
							return null;
						}

					}, true);
				}
				context.awaitAllTasks(new Runnable() {

					@Override
					public void run() {
						context.done(ver);
					}
				});
				return null;
			}
		});
	}

	private void handleVersionJson(final String version, final CombinedDownloadContext<Version> context, final Callable<?> callback) throws Exception {
		if (mcdir.getVersionJson(version).exists()) {
			JSONObject versionjson = readJson(mcdir.getVersionJson(version));
			String inheritsFrom = versionjson.optString("inheritsFrom", null);
			handledVersions.add(version);
			if (inheritsFrom == null) {
				// end node
				callback.call();
			} else {
				// intermediate node
				if (handledVersions.contains(inheritsFrom)) {
					throw new IllegalStateException("loop inherits from: " + version + " to " + inheritsFrom);
				}
				handleVersionJson(inheritsFrom, context, callback);
			}
		} else {
			context.submit(downloadProvider.gameVersionJson(mcdir, version), new CombinedDownloadCallback<Object>() {

				@Override
				public void done(Object result) {
					try {
						context.submit(new Callable<Object>() {

							@Override
							public Object call() throws Exception {
								handleVersionJson(version, context, callback);
								return null;
							}
						}, null, true);
					} catch (InterruptedException e) {
						context.cancelled();
					}
				}

				@Override
				public void failed(Throwable e) {
				}

				@Override
				public void cancelled() {
				}

				@Override
				public <R> DownloadCallback<R> taskStart(DownloadTask<R> task) {
					return null;
				}
			}, true);
		}
	}

	private void downloadAssets(final CombinedDownloadContext<Version> context, Set<Asset> assets) throws NoSuchAlgorithmException, IOException, InterruptedException {
		Map<String, Asset> hashMapping = new HashMap<>();
		for (Asset asset : assets) {
			// put the assets into a map
			// to remove the elements which has the same hash
			hashMapping.put(asset.getHash(), asset);
		}
		for (final Asset asset : hashMapping.values()) {
			context.submit(new Callable<Object>() {

				@Override
				public Object call() throws Exception {
					if (!asset.isValid(mcdir)) {
						context.submit(downloadProvider.asset(mcdir, asset), null, false);
					}
					return null;
				}
			}, null, false);
		}
	}

	private JSONObject readJson(File file) throws IOException, JSONException {
		try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), "UTF-8")) {
			return new JSONObject(new JSONTokener(reader));
		}
	}

}
