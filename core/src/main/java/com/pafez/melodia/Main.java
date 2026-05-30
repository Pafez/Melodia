package com.pafez.melodia;

import com.badlogic.gdx.Game;
import com.pafez.melodia.rhythm.RhythmGameScreen;

/** {@link com.badlogic.gdx.Game} entry point shared by all platforms. */
public class Main extends Game {

	@Override
	public void create() {
		setScreen(new RhythmGameScreen("charts/new_start.txt"));
	}
}