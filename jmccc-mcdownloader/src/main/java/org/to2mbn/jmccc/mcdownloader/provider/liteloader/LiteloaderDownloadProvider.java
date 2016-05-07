package org.to2mbn.jmccc.mcdownloader.provider.liteloader;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.to2mbn.jmccc.mcdownloader.download.FileDownloadTask;
import org.to2mbn.jmccc.mcdownloader.download.MemoryDownloadTask;
import org.to2mbn.jmccc.mcdownloader.download.ResultProcessor;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadContext;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadTask;
import org.to2mbn.jmccc.mcdownloader.provider.AbstractMinecraftDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.ExtendedDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.M2RepositorySupport;
import org.to2mbn.jmccc.mcdownloader.provider.MinecraftDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.JsonResultProcessor;
import org.to2mbn.jmccc.mcdownloader.provider.VersionJsonWriteProcessor;
import org.to2mbn.jmccc.mcdownloader.util.VersionComparator;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.util.IOUtils;
import org.to2mbn.jmccc.version.Library;

public class LiteloaderDownloadProvider extends AbstractMinecraftDownloadProvider implements ExtendedDownloadProvider {

	public static final String LITELOADER_GROUP_ID = "com.mumfrey";
	public static final String LITELOADER_ARTIFACT_ID = "liteloader";
	public static final String LITELOADER_TWEAK_CLASS = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
	public static final String LITELOADER_REPO_URL = "http://dl.liteloader.com/versions/";

	public static final String LAUNCH_WRAPPER_GROUP_ID = "net.minecraft";
	public static final String LAUNCH_WRAPPER_ARTIFACT_ID = "launchwrapper";
	public static final String LAUNCH_WRAPPER_LOWEST_VERSION = "1.7";
	public static final String LAUNCH_WRAPPER_MAINCLASS = "net.minecraft.launchwrapper.Launch";

	private LiteloaderDownloadSource source;
	private boolean upgradeLaunchWrapper = true;
	private String lowestLaunchWrapperVersion = LAUNCH_WRAPPER_LOWEST_VERSION;

	private VersionComparator versionComparator = new VersionComparator();
	private MinecraftDownloadProvider upstreamProvider;

	public LiteloaderDownloadProvider() {
		this(new DefaultLiteloaderDownloadSource());
	}

	public LiteloaderDownloadProvider(LiteloaderDownloadSource source) {
		this.source = Objects.requireNonNull(source);
	}

	public CombinedDownloadTask<LiteloaderVersionList> liteloaderVersionList() {
		return CombinedDownloadTask.single(new MemoryDownloadTask(source.getLiteloaderManifestUrl())
				.andThen(new JsonResultProcessor())
				.andThen(new ResultProcessor<JSONObject, LiteloaderVersionList>() {

					@Override
					public LiteloaderVersionList process(JSONObject json) throws Exception {
						return LiteloaderVersionList.fromJson(json);
					}
				})
				.cacheable());
	}

	@Override
	public CombinedDownloadTask<String> gameVersionJson(final MinecraftDirectory mcdir, String version) {
		final ResolvedLiteloaderVersion liteloaderInfo = ResolvedLiteloaderVersion.resolve(version);
		if (liteloaderInfo == null) {
			return null;
		}

		return upstreamProvider.gameVersionJson(mcdir, liteloaderInfo.getSuperVersion())
				.andThenDownload(new ResultProcessor<String, CombinedDownloadTask<LiteloaderVersion>>() {

					// lookup LiteloaderVersion
					@Override
					public CombinedDownloadTask<LiteloaderVersion> process(final String superVersion) throws Exception {
						return liteloaderVersionList()
								.andThen(new ResultProcessor<LiteloaderVersionList, LiteloaderVersion>() {

									@Override
									public LiteloaderVersion process(LiteloaderVersionList versionList) throws Exception {
										String mcversion = liteloaderInfo.getMinecraftVersion();
										LiteloaderVersion genericLiteloader = versionList.getLatest(mcversion);
										if (genericLiteloader == null) {
											genericLiteloader = versionList.getSnapshot(mcversion);
										}

										if (genericLiteloader == null) {
											throw new IllegalArgumentException("Liteloader version not found: " + liteloaderInfo);
										}
										return genericLiteloader.customize(superVersion);
									}
								});
					}
				})
				.andThenDownload(new ResultProcessor<LiteloaderVersion, CombinedDownloadTask<String>>() {

					// create version json
					@Override
					public CombinedDownloadTask<String> process(final LiteloaderVersion liteloader) throws Exception {
						if (liteloader.getLiteloaderVersion().endsWith("-SNAPSHOT")) {
							// it's a snapshot
							return source.liteloaderSnapshotVersionJson(liteloader)
									.andThen(new VersionJsonWriteProcessor(mcdir));
						} else {
							// it's a release
							return new CombinedDownloadTask<String>() {

								@Override
								public void execute(CombinedDownloadContext<String> context) throws Exception {
									context.done(new VersionJsonWriteProcessor(mcdir).process(createLiteloaderVersion(mcdir, liteloader)));
								}
							};
						}
					}
				});
	}

	@Override
	public CombinedDownloadTask<Void> library(final MinecraftDirectory mcdir, final Library library) {
		final String groupId = library.getGroupId();
		final String artifactId = library.getArtifactId();
		final String version = library.getVersion();

		if (LITELOADER_GROUP_ID.equals(groupId) && LITELOADER_ARTIFACT_ID.equals(artifactId)) {
			if (library.isSnapshotArtifact()) {
				return liteloaderVersionList()
						.andThenDownload(new ResultProcessor<LiteloaderVersionList, CombinedDownloadTask<Void>>() {

							@Override
							public CombinedDownloadTask<Void> process(LiteloaderVersionList versionList) throws Exception {
								LiteloaderVersion liteloader = versionList.getSnapshot(
										version.substring(0, version.length() - "-SNAPSHOT".length()) // the minecraft version
								);
								if (liteloader != null) {
									final String repo = liteloader.getRepoUrl();
									if (repo != null) {
										return M2RepositorySupport.snapshotPostfix(groupId, artifactId, version, repo)
												.andThenDownload(new ResultProcessor<String, CombinedDownloadTask<Void>>() {

													@Override
													public CombinedDownloadTask<Void> process(String postfix) throws Exception {
														Library libToDownload = new Library(groupId, artifactId, version, "release", library.getType());
														return CombinedDownloadTask.single(
																new FileDownloadTask(repo + libToDownload.getPath(postfix), mcdir.getLibrary(library))
																		.cacheable());
													}
												});
									}
								}
								return upstreamProvider.library(mcdir, library);
							}
						});
			}
		}
		return null;
	}

	@Override
	public void setUpstreamProvider(MinecraftDownloadProvider upstreamProvider) {
		this.upstreamProvider = upstreamProvider;
	}

	public boolean isUpgradeLaunchWrapper() {
		return upgradeLaunchWrapper;
	}

	public void setUpgradeLaunchWrapper(boolean upgradeLaunchWrapper) {
		this.upgradeLaunchWrapper = upgradeLaunchWrapper;
	}

	public String getLowestLaunchWrapperVersion() {
		return lowestLaunchWrapperVersion;
	}

	public void setLowestLaunchWrapperVersion(String lowestLaunchWrapperVersion) {
		this.lowestLaunchWrapperVersion = lowestLaunchWrapperVersion;
	}

	protected JSONObject createLiteloaderVersion(MinecraftDirectory mcdir, LiteloaderVersion liteloader) throws IOException {
		String superVersion = liteloader.getSuperVersion();
		String minecraftVersion = liteloader.getMinecraftVersion();
		String repoUrl = liteloader.getRepoUrl();
		String tweakClass = liteloader.getTweakClass();
		Set<JSONObject> liteloaderLibraries = liteloader.getLibraries();

		JSONObject versionjson = IOUtils.toJson(mcdir.getVersionJson(superVersion));

		String version = String.format("%s-LiteLoader%s", superVersion, minecraftVersion);
		String minecraftArguments = String.format("%s --tweakClass %s", versionjson.getString("minecraftArguments"),
				tweakClass == null ? LITELOADER_TWEAK_CLASS : tweakClass);
		JSONArray libraries = new JSONArray();
		JSONObject liteloaderLibrary = new JSONObject();
		liteloaderLibrary.put("name", String.format("%s:%s:%s", LITELOADER_GROUP_ID, LITELOADER_ARTIFACT_ID, minecraftVersion));
		liteloaderLibrary.put("url", repoUrl == null ? LITELOADER_REPO_URL : repoUrl);
		libraries.put(liteloaderLibrary);

		if (liteloaderLibraries != null) {
			for (JSONObject library : liteloaderLibraries) {

				if (upgradeLaunchWrapper) {
					String name = library.optString("name", null);
					if (name != null) {
						String launchwrapperPrefix = LAUNCH_WRAPPER_GROUP_ID + ":" + LAUNCH_WRAPPER_ARTIFACT_ID + ":";
						if (lowestLaunchWrapperVersion != null && name.startsWith(launchwrapperPrefix)) {
							String actualVersion = name.substring(launchwrapperPrefix.length());
							if (versionComparator.compare(actualVersion, lowestLaunchWrapperVersion) < 0) {
								library.put("name", launchwrapperPrefix + lowestLaunchWrapperVersion);
							}
						}
					}
				}

				libraries.put(library);
			}
		}

		versionjson.put("inheritsFrom", superVersion);
		versionjson.put("minecraftArguments", minecraftArguments);
		versionjson.put("mainClass", LAUNCH_WRAPPER_MAINCLASS);
		versionjson.put("id", version);
		versionjson.put("libraries", libraries);
		versionjson.remove("downloads");
		versionjson.remove("assets");
		versionjson.remove("assetIndex");
		return versionjson;
	}

}
