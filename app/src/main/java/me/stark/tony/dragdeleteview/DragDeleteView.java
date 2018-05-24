package me.stark.tony.dragdeleteview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

/**
 * @Descripation: 拖动删除控件<br>
 * @Date:2018/5/23 <br>
 */
public class DragDeleteView extends View {

    /**
     * 外界调用这个方法进行绑定实现拖拽删除的功能
     *
     * @param view
     */
    public static void attach(@NonNull View view, Callback callback) {
        view.setOnTouchListener(new DragDeleteTouchPerformerInternal(view, callback));
    }

    /**
     * 提供给外界回调的接口
     */
    public interface Callback {
        void onDelete();
    }

    private Bitmap mAnchorBitmap;
    private PointF mStartPoint;
    private PointF mCurPoint;
    private float mThresholdHeight;
    private Paint mPaint;
    private CallbackInternal mCallbackInternal;

    public DragDeleteView(Context context) {
        this(context, null);
    }

    public DragDeleteView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragDeleteView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mStartPoint = new PointF();
        mCurPoint = new PointF();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.DITHER_FLAG);
        mPaint.setTextSize(sp2px(15));
    }


    private void bindAnchorView(View anchorView) {
        mAnchorBitmap = getBitmapFromView(anchorView);
        int[] startArray = new int[2];
        anchorView.getLocationInWindow(startArray);
        mStartPoint.x = startArray[0];
        mStartPoint.y = startArray[1];
        mCurPoint.x = mStartPoint.x;
        mCurPoint.y = mStartPoint.y;
    }

    private Bitmap getBitmapFromView(View anchorView) {
        anchorView.buildDrawingCache();
        Bitmap bitmap = anchorView.getDrawingCache();
        return bitmap;
    }


    private void updateFingerPoint(float rawX, float rawY) {
        if (mAnchorBitmap == null) return;
        mCurPoint.x = rawX - mAnchorBitmap.getWidth() / 2;
        mCurPoint.y = rawY - mAnchorBitmap.getHeight() / 2;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 1. 绘制 Bitmap
        drawAnchorBitmap(canvas);
        // 2. 绘制底部的删除区域
        drawBottomRectAndText(canvas);
    }

    private void drawAnchorBitmap(Canvas canvas) {
        canvas.drawBitmap(mAnchorBitmap, mCurPoint.x,
                mCurPoint.y, mPaint);
    }

    private void drawBottomRectAndText(Canvas canvas) {
        if (mThresholdHeight == 0) {
            // 这里的阈值直接写死了
            mThresholdHeight = getMeasuredHeight() * 9f / 10f;
        }
        // 1. 绘制矩形
        RectF rectF = new RectF(0, mThresholdHeight, getWidth(), getHeight());
        mPaint.setColor(isOverThresholdHeight() ? Color.parseColor("#ffcc0000") : Color.parseColor("#ffff4444"));
        canvas.drawRect(rectF, mPaint);
        // 2. 绘制底部文本
        String str = isOverThresholdHeight() ? "松开删除" : "拖动到此处删除";
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float baseLineOffsetY = (fm.bottom - fm.top) / 2 - fm.bottom;
        float baseLine = (rectF.bottom + rectF.top) / 2 + baseLineOffsetY;
        float textStartX = getWidth() / 2 - mPaint.measureText(str) / 2;
        mPaint.setColor(Color.WHITE);
        canvas.drawText(str, textStartX, baseLine, mPaint);
    }

    /**
     * 判断是否拖拽超过了的阈值
     *
     * @return
     */
    public boolean isOverThresholdHeight() {
        return mCurPoint.y + mAnchorBitmap.getHeight() * 0.8f > mThresholdHeight;
    }

    /**
     * 供 DragDeleteTouchPerformerInternal 内部调用
     * <p>
     * 恢复原先位置
     */
    private void recover() {
        final float deltaX = mCurPoint.x - mStartPoint.x;
        final float deltaY = mCurPoint.y - mStartPoint.y;
        ValueAnimator anim = ValueAnimator.ofFloat(1f, 0f).setDuration(500);
        anim.setInterpolator(new OvershootInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCurPoint.x = mStartPoint.x + deltaX * (float) animation.getAnimatedValue();
                mCurPoint.y = mStartPoint.y + deltaY * (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCallbackInternal.onRecovered();
            }
        });
        anim.start();
    }

    private float sp2px(int sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    /**
     * 供 DragDeleteTouchPerformerInternal 内部回调
     */
    private interface CallbackInternal {
        void onRecovered();
    }


    private void setOnRecoverListener(CallbackInternal callbackInternal) {
        mCallbackInternal = callbackInternal;
    }

    /**
     * 被绑定的View滑动事件的处理类
     */
    private static class DragDeleteTouchPerformerInternal implements OnTouchListener, OnLongClickListener, CallbackInternal {

        final Context context;
        final View anchorView;
        final WindowManager wm;
        final WindowManager.LayoutParams params;
        final DragDeleteView dragDeleteView;
        final Callback callback;
        boolean isLongClicked = false;

        public DragDeleteTouchPerformerInternal(View anchorView, Callback callback) {
            this.callback = callback;
            this.anchorView = anchorView;
            context = anchorView.getContext();
            wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            params = new WindowManager.LayoutParams();
            params.flags = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;// 让当前Window占用状态栏空间
            params.format = PixelFormat.TRANSPARENT;
            params.width = context.getResources().getDisplayMetrics().widthPixels;
            params.height = context.getResources().getDisplayMetrics().heightPixels;
            dragDeleteView = new DragDeleteView(context);
            dragDeleteView.setOnRecoverListener(this);
            anchorView.setOnLongClickListener(this);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!isLongClicked) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE: {
                    // 1. 添加到 Window 中
                    if (!dragDeleteView.isAttachedToWindow()) {
                        dragDeleteView.bindAnchorView(anchorView);
                        wm.addView(dragDeleteView, params);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        v.setVisibility(View.INVISIBLE);
                    }
                    // 2. 更新坐标位置
                    dragDeleteView.updateFingerPoint(event.getRawX(), event.getRawY());
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    if (dragDeleteView.isOverThresholdHeight()) {
                        wm.removeView(dragDeleteView);
                        callback.onDelete();
                    } else {
                        dragDeleteView.recover();
                    }
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    isLongClicked = false;
                    break;
                }
            }
            return false;
        }

        @Override
        public void onRecovered() {
            if (dragDeleteView.isAttachedToWindow()) {
                wm.removeView(dragDeleteView);
                anchorView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            isLongClicked = true;
            return true;
        }
    }

}