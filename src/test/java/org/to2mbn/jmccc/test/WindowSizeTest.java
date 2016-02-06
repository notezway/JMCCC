package org.to2mbn.jmccc.test;

import static org.junit.Assert.*;
import org.junit.Test;
import org.to2mbn.jmccc.option.WindowSize;

public class WindowSizeTest {

	@Test
	public void testEqualsFullscreenSameSize() {
		assertTrue(WindowSize.fullscreen().equals(WindowSize.fullscreen()));
	}

	@Test
	public void testEqualsFullscreenDifferentSize() {
		WindowSize size = WindowSize.fullscreen();
		size.setWidth(100);
		assertTrue(WindowSize.fullscreen().equals(WindowSize.fullscreen()));
	}

	@Test
	public void testEqualsWindowSameSize() {
		assertTrue(new WindowSize(0, 100).equals(new WindowSize(0, 100)));
	}

	@Test
	public void testEqualsWindowDifferentSize() {
		assertFalse(new WindowSize(0, 100).equals(new WindowSize(100, 0)));
	}

	@Test
	public void testEqualsWindowDifferentSize2() {
		assertFalse(new WindowSize(0, 100).equals(new WindowSize(100, 100)));
	}

	@Test
	public void testEqualsFullscreenAndWindow() {
		assertFalse(new WindowSize(0, 0).equals(WindowSize.fullscreen()));
	}

}