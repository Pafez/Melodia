package com.pafez.melodia.rhythm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.pafez.melodia.Instrument;

public final class RhythmGameScreen implements Screen, InputProcessor {

	private static final Color BACKGROUND_TOP = new Color(0.08f, 0.10f, 0.14f, 1f);
	private static final Color BACKGROUND_BOTTOM = new Color(0.03f, 0.04f, 0.06f, 1f);
	private static final Color LANE_A = new Color(0.12f, 0.14f, 0.19f, 1f);
	private static final Color LANE_B = new Color(0.10f, 0.12f, 0.16f, 1f);
	private static final Color JUDGE_LINE = new Color(0.87f, 0.90f, 0.95f, 1f);
	private static final Color JUDGE_GLOW = new Color(0.35f, 0.45f, 0.72f, 0.35f);
	private static final Color TEXT_PRIMARY = new Color(0.96f, 0.98f, 1f, 1f);
	private static final Color TEXT_MUTED = new Color(0.72f, 0.78f, 0.87f, 1f);
	private static final Color[] LANE_COLORS = new Color[] {
		new Color(0.90f, 0.35f, 0.34f, 1f),
		new Color(0.95f, 0.62f, 0.22f, 1f),
		new Color(0.35f, 0.78f, 0.51f, 1f),
		new Color(0.27f, 0.63f, 0.94f, 1f),
		new Color(0.73f, 0.45f, 0.95f, 1f)
	};

	private final String chartPath;
	private final RhythmGameRules rules;
	private final OrthographicCamera camera = new OrthographicCamera();
	private final ShapeRenderer shapeRenderer = new ShapeRenderer();
	private final SpriteBatch batch = new SpriteBatch();
	private final BitmapFont font = new BitmapFont();
	private final GlyphLayout glyphLayout = new GlyphLayout();

	private RhythmChart chart;
	private RhythmLaneMapper laneMapper;
	private RhythmSession session;
	private Instrument instrument;
	private Throwable startupError;
	private long sessionStartNanos;
	private float lanePadding = 6f;

	public RhythmGameScreen(String chartPath) {
		this(chartPath, RhythmGameRules.defaultRules());
	}

	public RhythmGameScreen(String chartPath, RhythmGameRules rules) {
		if (chartPath == null || chartPath.trim().isEmpty()) {
			throw new IllegalArgumentException("Chart path cannot be empty.");
		}
		if (rules == null) {
			throw new IllegalArgumentException("Rules cannot be null.");
		}
		this.chartPath = chartPath;
		this.rules = rules;
	}

	@Override
	public void show() {
		sessionStartNanos = TimeUtils.nanoTime();
		startupError = null;
		try {
			String chartText = Gdx.files.internal(chartPath).readString("UTF-8");
			chart = RhythmChartParser.parse(chartPath, chartText);
			laneMapper = RhythmLaneMapper.fromChart(chart, rules.getLaneCount());
			session = new RhythmSession(chart, laneMapper, rules);
			try {
				instrument = Instrument.loadPiano();
			} catch (RuntimeException audioFailure) {
				instrument = null;
				Gdx.app.log("RhythmGameScreen", "Audio disabled: " + audioFailure.getMessage());
			}
		} catch (Throwable t) {
			startupError = t;
			Gdx.app.error("RhythmGameScreen", "Failed to start gameplay.", t);
		}
		Gdx.input.setInputProcessor(this);
	}

	@Override
	public void render(float delta) {
		float width = Gdx.graphics.getWidth();
		float height = Gdx.graphics.getHeight();
		camera.setToOrtho(false, width, height);
		camera.update();
		shapeRenderer.setProjectionMatrix(camera.combined);
		batch.setProjectionMatrix(camera.combined);

		Gdx.gl.glClearColor(BACKGROUND_BOTTOM.r, BACKGROUND_BOTTOM.g, BACKGROUND_BOTTOM.b, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		if (startupError != null) {
			renderStartupError(width, height);
			return;
		}

		long songTimeMs = getChartTimeMs();
		session.update(songTimeMs);

		drawBackground(width, height);
		drawLanes(width, height);
		drawNotes(width, height, songTimeMs);
		drawJudgeLine(width, height);
		drawHud(width, height, songTimeMs);
		if (session.isFinished()) {
			drawCompletionBanner(width, height);
		}
	}

	@Override
	public void resize(int width, int height) {
		camera.setToOrtho(false, width, height);
		camera.update();
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void hide() {
	}

	@Override
	public void dispose() {
		shapeRenderer.dispose();
		batch.dispose();
		font.dispose();
		if (instrument != null) {
			instrument.dispose();
		}
	}

	@Override
	public boolean keyDown(int keycode) {
		if (keycode == Input.Keys.ESCAPE) {
			Gdx.app.exit();
			return true;
		}
		if (keycode == Input.Keys.R) {
			restart();
			return true;
		}
		int lane = keycodeToLane(keycode);
		if (lane >= 0) {
			hitLane(lane);
			return true;
		}
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		if (button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT && button != 0) {
			return false;
		}
		if (laneMapper == null) {
			return false;
		}
		int lane = getLaneFromScreenX(screenX);
		if (lane < 0) {
			return false;
		}
		hitLane(lane);
		return true;
	}

	@Override public boolean keyUp(int keycode) { return false; }
	@Override public boolean keyTyped(char character) { return false; }
	@Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
	@Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
	@Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
	@Override public boolean mouseMoved(int screenX, int screenY) { return false; }
	@Override public boolean scrolled(float amountX, float amountY) { return false; }

	private void restart() {
		if (instrument != null) {
			instrument.dispose();
			instrument = null;
		}
		show();
	}

	private void hitLane(int lane) {
		if (session == null) {
			return;
		}
		long songTimeMs = getChartTimeMs();
		RhythmHitResult result = session.hitLane(lane, songTimeMs);
		if (result.isConsumed() && result.getNote() != null && instrument != null) {
			Sound sound = instrument.getSound(result.getNote().getMidiPitch());
			sound.play(0.85f);
		}
	}

	private void drawBackground(float width, float height) {
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		shapeRenderer.setColor(BACKGROUND_TOP);
		shapeRenderer.rect(0f, height * 0.48f, width, height * 0.52f);
		shapeRenderer.setColor(BACKGROUND_BOTTOM);
		shapeRenderer.rect(0f, 0f, width, height * 0.48f);
		shapeRenderer.end();
	}

	private void drawLanes(float width, float height) {
		float laneWidth = width / laneMapper.getLaneCount();
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		for (int lane = 0; lane < laneMapper.getLaneCount(); lane++) {
			shapeRenderer.setColor((lane % 2 == 0) ? LANE_A : LANE_B);
			shapeRenderer.rect(lane * laneWidth, 0f, laneWidth, height);
		}
		shapeRenderer.setColor(0f, 0f, 0f, 0.18f);
		for (int lane = 1; lane < laneMapper.getLaneCount(); lane++) {
			float x = lane * laneWidth;
			shapeRenderer.rect(x - 1f, 0f, 2f, height);
		}
		shapeRenderer.end();
	}

	private void drawJudgeLine(float width, float height) {
		float judgeY = getJudgeLineY(height);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		shapeRenderer.setColor(JUDGE_GLOW);
		shapeRenderer.rect(0f, judgeY - 14f, width, 28f);
		shapeRenderer.setColor(JUDGE_LINE);
		shapeRenderer.rect(0f, judgeY - 2f, width, 4f);
		shapeRenderer.end();
	}

	private void drawNotes(float width, float height, long songTimeMs) {
		float laneWidth = width / laneMapper.getLaneCount();
		float judgeY = getJudgeLineY(height);
		float travelDistance = height - judgeY - 24f;

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		for (int noteIndex = 0; noteIndex < chart.getNotes().size(); noteIndex++) {
			if (session.isResolved(noteIndex)) {
				continue;
			}
			RhythmNote note = chart.getNotes().get(noteIndex);
			float centerY = timeToY(note.getStartTimeMs(), songTimeMs, judgeY, travelDistance);
			if (centerY < -80f || centerY > height + 120f) {
				continue;
			}

			int lane = laneMapper.getLaneForPitch(note.getMidiPitch());
			float laneX = lane * laneWidth;
			float noteWidth = laneWidth - (lanePadding * 2f);
			float noteHeight = note.isHoldNote()
					? Math.max(28f, timeSpanToPixels(note.getEndTimeMs() - note.getStartTimeMs(), travelDistance))
					: 22f;
			float drawX = laneX + lanePadding;
			float drawY = centerY - noteHeight / 2f;

			Color laneColor = LANE_COLORS[lane % LANE_COLORS.length];
			shapeRenderer.setColor(0f, 0f, 0f, 0.35f);
			shapeRenderer.rect(drawX + 3f, drawY - 3f, noteWidth, noteHeight);
			shapeRenderer.setColor(laneColor);
			shapeRenderer.rect(drawX, drawY, noteWidth, noteHeight);
			shapeRenderer.setColor(1f, 1f, 1f, 0.18f);
			shapeRenderer.rect(drawX + 3f, drawY + noteHeight - 5f, noteWidth - 6f, 3f);
		}
		shapeRenderer.end();
	}

	private void drawHud(float width, float height, long songTimeMs) {
		batch.begin();
		font.setColor(TEXT_PRIMARY);
		float x = 18f;
		float y = height - 18f;

		drawText("Melodia Rhythm Demo", x, y, TEXT_PRIMARY);
		drawText("Score: " + session.getScoreState().getScore(), x, y - 28f, TEXT_PRIMARY);
		drawText("Combo: " + session.getScoreState().getCombo() + "  x" + formatMultiplier(session.getScoreState().getMultiplier()), x, y - 52f, TEXT_PRIMARY);
		drawText("Accuracy: " + formatPercent(session.getScoreState().getAccuracyPercent()) + "%", x, y - 76f, TEXT_MUTED);
		drawText("Perfect: " + session.getScoreState().getPerfectCount() + "  Great: " + session.getScoreState().getGreatCount()
				+ "  Good: " + session.getScoreState().getGoodCount() + "  Miss: " + session.getScoreState().getMissCount(), x, y - 100f, TEXT_MUTED);

		String judgment = session.getScoreState().getLastJudgment().getLabel();
		if (!judgment.isEmpty() && songTimeMs - session.getScoreState().getLastJudgmentTimeMs() <= 900L) {
			glyphLayout.setText(font, judgment);
			float textX = width * 0.5f - glyphLayout.width * 0.5f;
			float textY = height * 0.72f;
			font.setColor(colorForJudgment(session.getScoreState().getLastJudgment()));
			font.draw(batch, judgment, textX, textY);
		}

		font.setColor(TEXT_MUTED);
		String help = "Keys: A S D F G or click the lanes | R restart | Esc exit";
		glyphLayout.setText(font, help);
		font.draw(batch, help, width - glyphLayout.width - 18f, 24f);
		batch.end();
	}

	private void drawCompletionBanner(float width, float height) {
		batch.begin();
		String message = "Chart complete";
		glyphLayout.setText(font, message);
		font.setColor(TEXT_PRIMARY);
		font.draw(batch, message, width * 0.5f - glyphLayout.width * 0.5f, height * 0.42f);
		batch.end();
	}

	private void renderStartupError(float width, float height) {
		batch.begin();
		font.setColor(new Color(0.96f, 0.46f, 0.46f, 1f));
		String title = "Failed to start rhythm demo";
		glyphLayout.setText(font, title);
		font.draw(batch, title, width * 0.5f - glyphLayout.width * 0.5f, height * 0.62f);
		font.setColor(TEXT_MUTED);
		String message = startupError.getMessage() == null ? startupError.getClass().getSimpleName() : startupError.getMessage();
		glyphLayout.setText(font, message);
		font.draw(batch, message, width * 0.5f - glyphLayout.width * 0.5f, height * 0.54f);
		batch.end();
	}

	private void drawText(String text, float x, float y, Color color) {
		font.setColor(color);
		font.draw(batch, text, x, y);
	}

	private float getJudgeLineY(float height) {
		return Math.max(82f, height * 0.18f);
	}

	private float timeToY(long noteTimeMs, long songTimeMs, float judgeY, float travelDistance) {
		float offsetMs = noteTimeMs - songTimeMs;
		return judgeY + (offsetMs / rules.getTravelTimeMs()) * travelDistance;
	}

	private float timeSpanToPixels(long timeSpanMs, float travelDistance) {
		return Math.max(28f, (timeSpanMs / (float) rules.getTravelTimeMs()) * travelDistance);
	}

	private String formatMultiplier(float value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private String formatPercent(float value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private Color colorForJudgment(RhythmJudgment judgment) {
		if (judgment == RhythmJudgment.PERFECT) {
			return new Color(0.89f, 0.96f, 0.50f, 1f);
		}
		if (judgment == RhythmJudgment.GREAT) {
			return new Color(0.45f, 0.86f, 0.95f, 1f);
		}
		if (judgment == RhythmJudgment.GOOD) {
			return new Color(0.54f, 0.92f, 0.60f, 1f);
		}
		return new Color(0.96f, 0.60f, 0.44f, 1f);
	}

	private int keycodeToLane(int keycode) {
		switch (keycode) {
			case Input.Keys.A:
				return 0;
			case Input.Keys.S:
				return 1;
			case Input.Keys.D:
				return 2;
			case Input.Keys.F:
				return 3;
			case Input.Keys.G:
				return 4;
			default:
				return -1;
		}
	}

	private int getLaneFromScreenX(int screenX) {
		if (laneMapper == null) {
			return -1;
		}
		float width = Gdx.graphics.getWidth();
		float laneWidth = width / laneMapper.getLaneCount();
		int lane = MathUtils.floor(screenX / laneWidth);
		if (lane < 0 || lane >= laneMapper.getLaneCount()) {
			return -1;
		}
		return lane;
	}

	private long getChartTimeMs() {
		return Math.round(TimeUtils.timeSinceNanos(sessionStartNanos) / 1_000_000.0) - rules.getLeadInMs();
	}
}