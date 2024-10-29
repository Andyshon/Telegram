package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;

public class StartBotTooltipLayout extends LinearLayout {

    private final RectF rectF = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath1 = new Path();
    private final Path arrowPath2 = new Path();

    private float arrow1OffsetY = 0;
    private float arrow2OffsetY = 0;

    private final int animEntranceDuration = 300;
    private final int animDuration = 1500;
    private final int animDelay = 2000;

    private Handler handler;
    private boolean canStartAppear = true;
    private AnimatorSet enterExitAnimSet, bounceAnimSet, arrowAnimSet;

    private final int dp16 = AndroidUtilities.dp(16);
    private final int dp22 = AndroidUtilities.dp(22);
    private final int dp28 = AndroidUtilities.dp(28);
    private final int dp8 = AndroidUtilities.dp(8);
    private final int dp2 = AndroidUtilities.dp(2);
    private final int dp4 = AndroidUtilities.dp(4);
    private final int dp3 = AndroidUtilities.dp(3);

    private final int radius = dp8;
    private final int triangleHeight = dp8;

    public StartBotTooltipLayout(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        paint.setColor(0xCC272f38);
        paint.setStrokeCap(Paint.Cap.ROUND);

        arrowPaint.setColor(ContextCompat.getColor(context, android.R.color.white));
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);

        TextView textView = new TextView(context);
        textView.setText("Tap here to use this bot");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setIncludeFontPadding(false);
        textView.setTextColor(ContextCompat.getColor(context, android.R.color.white));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
        );
        textParams.leftMargin = AndroidUtilities.dp(28 + 12);
        textParams.rightMargin = AndroidUtilities.dp(16);
        int verticalPadding = AndroidUtilities.dp(12);
        textParams.topMargin = verticalPadding;
        textParams.bottomMargin = verticalPadding + triangleHeight / 2;
        addView(textView, textParams);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float halfWidth = (float) getWidth() / 2;
        float halfHeight = (float) getHeight() / 2;

        path.reset();
        rectF.set(0f, 0f, getWidth(), getHeight() - triangleHeight);
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW);
        path.moveTo(halfWidth - triangleHeight, getHeight() - triangleHeight);
        path.lineTo(halfWidth, getHeight());
        path.lineTo(halfWidth + triangleHeight, getHeight() - triangleHeight);
        path.close();
        canvas.drawPath(path, paint);

        arrowPath1.reset();
        arrowPath1.moveTo(dp16, halfHeight - dp8 + arrow1OffsetY);
        arrowPath1.lineTo(dp22, halfHeight - dp2 + arrow1OffsetY);
        arrowPath1.lineTo(dp28, halfHeight - dp8 + arrow1OffsetY);
        canvas.drawPath(arrowPath1, arrowPaint);

        arrowPath2.reset();
        arrowPath2.moveTo(dp16, halfHeight - dp2 + arrow2OffsetY);
        arrowPath2.lineTo(dp22, halfHeight + dp4 + arrow2OffsetY);
        arrowPath2.lineTo(dp28, halfHeight - dp2 + arrow2OffsetY);
        canvas.drawPath(arrowPath2, arrowPaint);
    }

    public void startAppearAnimation() {
        if (!canStartAppear) {
            return;
        }
        canStartAppear = false;

        cancelAllAnimations();

        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(this, "scaleX", 0f, 1f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(this, "scaleY", 0f, 1f);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f);
        ObjectAnimator translationYAnimator = ObjectAnimator.ofFloat(this, "translationY", AndroidUtilities.dp(48+8), 0f);

        scaleXAnimator.setDuration(animEntranceDuration);
        scaleYAnimator.setDuration(animEntranceDuration);
        alphaAnimator.setDuration(animEntranceDuration);
        translationYAnimator.setDuration(animEntranceDuration);

        enterExitAnimSet = new AnimatorSet();
        enterExitAnimSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator, translationYAnimator);
        enterExitAnimSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handler = new Handler();
                startBounceAnimation();
                startArrowAnimations();
            }
        });
        enterExitAnimSet.start();
    }

    public void startDisappearAnimation() {
        cancelAllAnimations();
        canStartAppear = true;

        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(this, "scaleY", 1f, 0f);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        ObjectAnimator translationYAnimator = ObjectAnimator.ofFloat(this, "translationY", 0f, AndroidUtilities.dp(48+8));

        scaleXAnimator.setDuration(animEntranceDuration);
        scaleYAnimator.setDuration(animEntranceDuration);
        alphaAnimator.setDuration(animEntranceDuration);
        translationYAnimator.setDuration(animEntranceDuration);

        enterExitAnimSet = new AnimatorSet();
        enterExitAnimSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator, translationYAnimator);
        enterExitAnimSet.start();
    }

    private void startBounceAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, "translationY", 0, dp3, 0, dp3, 0);
        animator.setDuration(animDuration);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        bounceAnimSet = new AnimatorSet();
        bounceAnimSet.play(animator);
        bounceAnimSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (handler != null) {
                    handler.postDelayed(bounceAnimSet::start, animDelay);
                }
            }
        });
        bounceAnimSet.start();
    }

    private void startArrowAnimations() {
        @SuppressLint("ObjectAnimatorBinding")
        ObjectAnimator arrow1Animator = ObjectAnimator.ofFloat(this, "arrow1OffsetY", 0, dp4, 0, dp4, 0);
        arrow1Animator.setDuration(animDuration);
        arrow1Animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        @SuppressLint("ObjectAnimatorBinding")
        ObjectAnimator arrow2Animator = ObjectAnimator.ofFloat(this, "arrow2OffsetY", 0, dp3, 0, dp3, 0);
        arrow2Animator.setDuration(animDuration);
        arrow2Animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        arrowAnimSet = new AnimatorSet();
        arrowAnimSet.playTogether(arrow1Animator, arrow2Animator);
        arrowAnimSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (handler != null) {
                    handler.postDelayed(arrowAnimSet::start, animDelay);
                }
            }
        });
        arrowAnimSet.start();
    }

    private void cancelAllAnimations() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (enterExitAnimSet != null) {
            enterExitAnimSet.cancel();
        }
        if (bounceAnimSet != null) {
            bounceAnimSet.cancel();
        }
        if (arrowAnimSet != null) {
            arrowAnimSet.cancel();
        }
    }

    @SuppressLint("AnimatorKeep")
    public void setArrow1OffsetY(float offsetY) {
        this.arrow1OffsetY = offsetY;
        invalidate();
    }

    @SuppressLint("AnimatorKeep")
    public void setArrow2OffsetY(float offsetY) {
        this.arrow2OffsetY = offsetY;
        invalidate();
    }
}