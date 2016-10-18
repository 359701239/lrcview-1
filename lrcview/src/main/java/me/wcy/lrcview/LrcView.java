package me.wcy.lrcview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词
 * Created by wcy on 2015/11/9.
 */
public class LrcView extends View {
    private List<Long> mLrcTimes = new ArrayList<>();
    private List<String> mLrcTexts = new ArrayList<>();
    private Paint mNormalPaint = new Paint();
    private Paint mCurrentPaint = new Paint();
    private float mTextSize;
    private float mDividerHeight;
    private long mAnimationDuration;
    private float mAnimOffset;
    private long mNextTime = 0L;
    private int mCurrentLine = 0;
    private boolean isEnd = false;
    private String label;

    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LrcView);
        mTextSize = ta.getDimension(R.styleable.LrcView_lrcTextSize, sp2px(16));
        mDividerHeight = ta.getDimension(R.styleable.LrcView_lrcDividerHeight, dp2px(24));
        mAnimationDuration = ta.getInt(R.styleable.LrcView_lrcAnimationDuration, 1000);
        mAnimationDuration = mAnimationDuration < 0 ? 1000 : mAnimationDuration;
        int normalColor = ta.getColor(R.styleable.LrcView_lrcNormalTextColor, 0xFFFFFFFF);
        int currentColor = ta.getColor(R.styleable.LrcView_lrcCurrentTextColor, 0xFFFF4081);
        label = ta.getString(R.styleable.LrcView_lrcLabel);
        label = TextUtils.isEmpty(label) ? "暂无歌词" : label;
        ta.recycle();

        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setColor(normalColor);
        mNormalPaint.setTextSize(mTextSize);
        mNormalPaint.setTextAlign(Paint.Align.CENTER);

        mCurrentPaint.setAntiAlias(true);
        mCurrentPaint.setColor(currentColor);
        mCurrentPaint.setTextSize(mTextSize);
        mCurrentPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 中心Y坐标
        float centerY = getHeight() / 2 + mTextSize / 2 + mAnimOffset;

        // 无歌词文件
        if (!hasLrc()) {
            float centerX = getWidth() / 2;
            canvas.drawText(label, centerX, centerY, mCurrentPaint);
            return;
        }

        // 画当前行
        String currStr = mLrcTexts.get(mCurrentLine);
        float currX = getWidth() / 2;
        canvas.drawText(currStr, currX, centerY, mCurrentPaint);

        // 画当前行上面的
        for (int i = mCurrentLine - 1; i >= 0; i--) {
            String upStr = mLrcTexts.get(i);
            float upX = getWidth() / 2;
            float upY = centerY - (mTextSize + mDividerHeight) * (mCurrentLine - i);
            // 超出屏幕停止绘制
            if (upY - mTextSize < 0) {
                break;
            }
            canvas.drawText(upStr, upX, upY, mNormalPaint);
        }

        // 画当前行下面的
        for (int i = mCurrentLine + 1; i < mLrcTimes.size(); i++) {
            String downStr = mLrcTexts.get(i);
            float downX = getWidth() / 2;
            float downY = centerY + (mTextSize + mDividerHeight) * (i - mCurrentLine);
            // 超出屏幕停止绘制
            if (downY > getHeight()) {
                break;
            }
            canvas.drawText(downStr, downX, downY, mNormalPaint);
        }
    }

    /**
     * 设置歌词为空时屏幕中央显示的文字，如“暂无歌词”
     */
    public void setLabel(String label) {
        reset();

        this.label = label;
        postInvalidate();
    }

    /**
     * 加载歌词文件
     *
     * @param lrcFile 歌词文件
     */
    public void loadLrc(File lrcFile) {
        reset();

        if (lrcFile == null || !lrcFile.exists()) {
            postInvalidate();
            return;
        }

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(lrcFile), "utf-8"));
            String line;
            while ((line = br.readLine()) != null) {
                parseLine(line);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        initNextTime();

        postInvalidate();
    }

    /**
     * 加载歌词文件
     *
     * @param lrcText 歌词文本
     */
    public void loadLrc(String lrcText) {
        reset();

        if (TextUtils.isEmpty(lrcText)) {
            postInvalidate();
            return;
        }

        String[] array = lrcText.split("\\n");
        for (String line : array) {
            parseLine(line);
        }

        initNextTime();

        postInvalidate();
    }

    /**
     * 刷新歌词
     *
     * @param time 当前播放时间
     */
    public void updateTime(long time) {
        // 避免重复绘制
        if (time < mNextTime || isEnd) {
            return;
        }
        for (int i = mCurrentLine; i < mLrcTimes.size(); i++) {
            if (mLrcTimes.get(i) > time) {
                mNextTime = mLrcTimes.get(i);
                mCurrentLine = i < 1 ? 0 : i - 1;
                newLineAnim();
                break;
            } else if (i == mLrcTimes.size() - 1) {
                // 最后一行
                mCurrentLine = mLrcTimes.size() - 1;
                isEnd = true;
                newLineAnim();
                break;
            }
        }
    }

    /**
     * 将歌词滚动到指定时间
     *
     * @param time 指定的时间
     */
    public void onDrag(long time) {
        for (int i = 0; i < mLrcTimes.size(); i++) {
            if (mLrcTimes.get(i) > time) {
                mNextTime = mLrcTimes.get(i);
                mCurrentLine = i < 1 ? 0 : i - 1;
                isEnd = false;
                newLineAnim();
                break;
            }
        }
    }

    /**
     * 歌词是否有效
     *
     * @return true，如果歌词有效，否则false
     */
    public boolean hasLrc() {
        return mLrcTexts != null && !mLrcTexts.isEmpty();
    }

    private void reset() {
        mLrcTexts.clear();
        mLrcTimes.clear();
        mCurrentLine = 0;
        mNextTime = 0L;
        isEnd = false;
    }

    private void initNextTime() {
        if (mLrcTimes.size() > 1) {
            mNextTime = mLrcTimes.get(1);
        }
    }

    /**
     * 解析一行
     *
     * @param line [00:10.61]走过了人来人往
     */
    private void parseLine(String line) {
        line = line.trim();
        Matcher matcher = Pattern.compile("\\[(\\d\\d):(\\d\\d)\\.(\\d\\d)\\](.+)").matcher(line);
        if (!matcher.matches()) {
            return;
        }

        long min = Long.parseLong(matcher.group(1));
        long sec = Long.parseLong(matcher.group(2));
        long mil = Long.parseLong(matcher.group(3));
        String text = matcher.group(4);

        long time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil * 10;

        mLrcTimes.add(time);
        mLrcTexts.add(text);
    }

    /**
     * 换行动画<br>
     * 属性动画只能在主线程使用
     */
    private void newLineAnim() {
        ValueAnimator animator = ValueAnimator.ofFloat(mTextSize + mDividerHeight, 0.0f);
        animator.setDuration(mAnimationDuration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimOffset = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    private int dp2px(float dpValue) {
        float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    private int sp2px(float spValue) {
        float fontScale = getContext().getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }
}
