package org.to2mbn.jmccc.mcdownloader;

import java.util.Objects;
import org.to2mbn.jmccc.mcdownloader.download.Downloader;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloader;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloaderBuilder;
import org.to2mbn.jmccc.mcdownloader.provider.DownloadProviderChain;
import org.to2mbn.jmccc.mcdownloader.provider.MinecraftDownloadProvider;
import org.to2mbn.jmccc.util.Builder;

public class MinecraftDownloaderBuilder implements Builder<MinecraftDownloader> {

	public static MinecraftDownloaderBuilder create(Builder<? extends Downloader> underlying) {
		return new MinecraftDownloaderBuilder(underlying);
	}

	public static MinecraftDownloaderBuilder create() {
		return create(CombinedDownloaderBuilder.create());
	}

	public static MinecraftDownloader buildDefault(Builder<? extends Downloader> underlying) {
		return new MinecraftDownloaderBuilder(underlying).build();
	}

	public static MinecraftDownloader buildDefault() {
		return buildDefault(CombinedDownloaderBuilder.create());
	}

	protected final Builder<? extends Downloader> underlying;
	protected boolean checkLibrariesHash = false;
	protected boolean checkAssetsHash = false;
	protected boolean updateSnapshots = true;
	protected Builder<MinecraftDownloadProvider> providerChain;

	protected MinecraftDownloaderBuilder(Builder<? extends Downloader> underlying) {
		this.underlying = underlying;
	}

	public MinecraftDownloaderBuilder checkLibrariesHash(boolean checkLibrariesHash) {
		this.checkLibrariesHash = checkLibrariesHash;
		return this;
	}

	public MinecraftDownloaderBuilder checkAssetsHash(boolean checkAssetsHash) {
		this.checkAssetsHash = checkAssetsHash;
		return this;
	}

	public MinecraftDownloaderBuilder updateSnapshots(boolean updateSnapshots) {
		this.updateSnapshots = updateSnapshots;
		return this;
	}

	public MinecraftDownloaderBuilder providerChain(Builder<MinecraftDownloadProvider> providerChain) {
		this.providerChain = providerChain;
		return this;
	}

	@Override
	public MinecraftDownloader build() {
		MinecraftDownloadProvider provider = providerChain == null
				? DownloadProviderChain.buildDefault()
				: Objects.requireNonNull(providerChain.build(), "providerChain builder returns null");

		CombinedDownloader combinedDownloader = null;
		Downloader underlying = null;
		try {
			underlying = Objects.requireNonNull(this.underlying.build(), "Underlying downloader builder returns null");

			if (underlying instanceof CombinedDownloader) {
				combinedDownloader = (CombinedDownloader) underlying;
			} else {
				combinedDownloader = CombinedDownloaderBuilder.buildDefault();
			}

			return new MinecraftDownloaderImpl(combinedDownloader, provider, checkLibrariesHash, checkAssetsHash, updateSnapshots);
		} catch (Throwable e) {
			if (combinedDownloader != null) {
				try {
					combinedDownloader.shutdown();
				} catch (Throwable e1) {
					e.addSuppressed(e1);
				}
			}
			if (underlying != null) {
				try {
					underlying.shutdown();
				} catch (Throwable e1) {
					e.addSuppressed(e1);
				}
			}
			throw e;
		}
	}

}
