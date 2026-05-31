package com.pafez.melodia.rhythm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
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

import java.util.HashMap;
import java.util.Map;

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
	private final RhythmGameRules baseRules;
	private final RhythmKeyBindings baseKeyBindings;
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
	private RhythmGameRules activeRules;
	private RhythmKeyBindings activeKeyBindings;
	private float lanePadding = 6f;
	private final int[] lanePressCounts = new int[RhythmGameRules.MAX_LANE_COUNT];
	private final Map<Integer, Integer> touchLaneByPointer = new HashMap<>();

	public RhythmGameScreen(String chartPath) {
		this(chartPath, RhythmGameRules.defaultRules(), RhythmKeyBindings.defaultNumberRow());
	}

	public RhythmGameScreen(String chartPath, RhythmGameRules rules) {
		this(chartPath, rules, RhythmKeyBindings.defaultNumberRow());
	}

	public RhythmGameScreen(String chartPath, RhythmGameRules rules, RhythmKeyBindings keyBindings) {
		if (chartPath == null || chartPath.trim().isEmpty()) {
			throw new IllegalArgumentException("Chart path cannot be empty.");
		}
		if (rules == null) {
			throw new IllegalArgumentException("Rules cannot be null.");
		}
		if (keyBindings == null) {
			throw new IllegalArgumentException("Key bindings cannot be null.");
		}
		this.chartPath = chartPath;
		this.baseRules = rules;
		this.baseKeyBindings = keyBindings;
		this.activeRules = rules;
		this.activeKeyBindings = keyBindings;
	}

	public int getColumnCount() {
		return activeRules.getLaneCount();
	}

	public void setColumnCount(int columnCount) {
		if (columnCount > activeKeyBindings.getLaneCount()) {
			throw new IllegalArgumentException("Current key bindings only define " + activeKeyBindings.getLaneCount()
					+ " lanes. Update the key bindings first.");
		}
		activeRules = baseRules.withLaneCount(columnCount);
		if (chart != null) {
			restart();
		}
	}

	public RhythmKeyBindings getKeyBindings() {
		return activeKeyBindings;
	}

	public void setKeyBindings(RhythmKeyBindings keyBindings) {
		if (keyBindings == null) {
			throw new IllegalArgumentException("Key bindings cannot be null.");
		}
		if (keyBindings.getLaneCount() < activeRules.getLaneCount()) {
			throw new IllegalArgumentException("Key bindings must define at least " + activeRules.getLaneCount()
					+ " keys for the current column count.");
		}
		activeKeyBindings = keyBindings;
		if (chart != null) {
			restart();
		}
	}

	@Override
	public void show() {
		sessionStartNanos = TimeUtils.nanoTime();
		startupError = null;
		try {
			String chartText = readChartText(chartPath);
			chart = RhythmChartParser.parse(chartPath, chartText);
			laneMapper = RhythmLaneMapper.fromChart(chart, activeRules.getLaneCount());
			session = new RhythmSession(chart, laneMapper, activeRules);
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
		int lane = activeKeyBindings.findLaneForKeyCode(keycode, activeRules.getLaneCount());
		if (lane >= 0) {
			lanePressCounts[lane]++;
			if (lanePressCounts[lane] == 1) {
				pressLane(lane);
			}
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
		touchLaneByPointer.put(pointer, lane);
		lanePressCounts[lane]++;
		if (lanePressCounts[lane] == 1) {
			pressLane(lane);
		}
		return true;
	}

	@Override
	public boolean keyUp(int keycode) {
		int lane = activeKeyBindings.findLaneForKeyCode(keycode, activeRules.getLaneCount());
		if (lane < 0) {
			return false;
		}
		if (lanePressCounts[lane] > 0) {
			lanePressCounts[lane]--;
			if (lanePressCounts[lane] == 0) {
				releaseLane(lane);
			}
		}
		return true;
	}

	@Override public boolean keyTyped(char character) { return false; }
	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		Integer lane = touchLaneByPointer.remove(pointer);
		if (lane == null) {
			return false;
		}
		if (lanePressCounts[lane] > 0) {
			lanePressCounts[lane]--;
			if (lanePressCounts[lane] == 0) {
				releaseLane(lane);
			}
		}
		return true;
	}
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

	private void pressLane(int lane) {
		if (session == null) {
			return;
		}
		long songTimeMs = getChartTimeMs();
		RhythmHitResult result = session.pressLane(lane, songTimeMs);
		if (result.isConsumed() && result.getNote() != null && instrument != null) {
			instrument.play(result.getNote().getMidiPitch(), 0.85f);
		}
	}

	private void releaseLane(int lane) {
		if (session == null) {
			return;
		}
		session.releaseLane(lane, getChartTimeMs());
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

			int lane = laneMapper.getLaneForPitch(note.getMidiPitch());
			float laneX = lane * laneWidth;
			float noteWidth = laneWidth - (lanePadding * 2f);
			float drawX = laneX + lanePadding;

			float drawY;
			float noteHeight;
			if (note.isHoldNote()) {
				float startY = timeToY(note.getStartTimeMs(), songTimeMs, judgeY, travelDistance);
				float endY = timeToY(note.getEndTimeMs(), songTimeMs, judgeY, travelDistance);
				drawY = Math.min(startY, endY);
				noteHeight = Math.max(28f, Math.abs(endY - startY));
				if (drawY + noteHeight < -80f || drawY > height + 120f) {
					continue;
				}
			} else {
				float centerY = timeToY(note.getStartTimeMs(), songTimeMs, judgeY, travelDistance);
				if (centerY < -80f || centerY > height + 120f) {
					continue;
				}
				noteHeight = 22f;
				drawY = centerY - noteHeight / 2f;
			}

			Color laneColor = LANE_COLORS[lane % LANE_COLORS.length];
			shapeRenderer.setColor(0f, 0f, 0f, 0.35f);
			shapeRenderer.rect(drawX + 3f, drawY - 3f, noteWidth, noteHeight);
			shapeRenderer.setColor(laneColor);
			shapeRenderer.rect(drawX, drawY, noteWidth, noteHeight);
			shapeRenderer.setColor(1f, 1f, 1f, 0.18f);
			shapeRenderer.rect(drawX + 3f, drawY + noteHeight - 5f, noteWidth - 6f, 3f);
			if (note.isHoldNote() && session.isHoldNoteActive(noteIndex)) {
				shapeRenderer.setColor(1f, 1f, 1f, 0.12f);
				shapeRenderer.rect(drawX, drawY, noteWidth, noteHeight);
			}
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
		drawText("Columns: " + activeRules.getLaneCount() + " (3-9)", x, y - 100f, TEXT_MUTED);
		drawText("Perfect: " + session.getScoreState().getPerfectCount() + "  Great: " + session.getScoreState().getGreatCount()
				+ "  Good: " + session.getScoreState().getGoodCount() + "  Miss: " + session.getScoreState().getMissCount(), x, y - 124f, TEXT_MUTED);

		String judgment = session.getScoreState().getLastJudgment().getLabel();
		if (!judgment.isEmpty() && songTimeMs - session.getScoreState().getLastJudgmentTimeMs() <= 900L) {
			glyphLayout.setText(font, judgment);
			float textX = width * 0.5f - glyphLayout.width * 0.5f;
			float textY = height * 0.72f;
			font.setColor(colorForJudgment(session.getScoreState().getLastJudgment()));
			font.draw(batch, judgment, textX, textY);
		}

		font.setColor(TEXT_MUTED);
		String help = "Keys: " + activeKeyBindings.describe(activeRules.getLaneCount()) + " | click lanes | R restart | Esc exit";
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
		return judgeY + (offsetMs / activeRules.getTravelTimeMs()) * travelDistance;
	}

	private float timeSpanToPixels(long timeSpanMs, float travelDistance) {
		return Math.max(28f, (timeSpanMs / (float) activeRules.getTravelTimeMs()) * travelDistance);
	}

	private String formatMultiplier(float value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private String formatPercent(float value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private String readChartText(String path) {
		String[] candidatePaths = new String[] {
			path,
			"assets/" + path
		};
		for (String candidatePath : candidatePaths) {
			if (Gdx.files.local(candidatePath).exists()) {
				return Gdx.files.local(candidatePath).readString("UTF-8");
			}
		}
		return Gdx.files.internal(path).readString("UTF-8");
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
		return Math.round(TimeUtils.timeSinceNanos(sessionStartNanos) / 1_000_000.0) - activeRules.getLeadInMs();
	}
}