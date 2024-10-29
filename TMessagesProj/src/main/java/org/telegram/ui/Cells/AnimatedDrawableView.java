package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

// not finished and not used
public class AnimatedDrawableView extends View {
    private Drawable drawableIcon;
    private boolean shouldAnimateSideButton;
    private final Theme.ResourcesProvider resourcesProvider;
    private float sideStartX, sideStartY;
    private float rotationAngle = 0;
    private float arrowOffsetY = 0;
    private Matrix matrix = new Matrix();

    public AnimatedDrawableView(Context context, Theme.ResourcesProvider resourcesProvider, RectF rect) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        init();
    }

    public void setSideStartX(float sideStartX) {
        this.sideStartX = sideStartX;
    }

    public void setSideStartY(float sideStartY) {
        this.sideStartY = sideStartY;
    }

    private void init() {
        drawableIcon = getThemedDrawable(Theme.key_drawable_shareIcon);
    }

    private Drawable getThemedDrawable(String key) {
        Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(key) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(key);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int scx = (int) (sideStartX + AndroidUtilities.dp(16));
        final int scy = (int) (sideStartY + AndroidUtilities.dp(16 + arrowOffsetY));
        Drawable drawable = drawableIcon;
        final int shw = drawable.getIntrinsicWidth() / 2, shh = drawable.getIntrinsicHeight() / 2;
        drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);

        matrix.reset();
        matrix.postRotate(rotationAngle, scx, scy);
        canvas.save();
        canvas.concat(matrix);
        drawable.draw(canvas);
        canvas.restore();

        if (shouldAnimateSideButton) {
            shouldAnimateSideButton = false;

            ObjectAnimator translationY = ObjectAnimator.ofFloat(this, "arrowOffsetY", 0, -AndroidUtilities.dp(8), 0);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(translationY);
            animatorSet.setDuration(1_000);
            animatorSet.start();

            ObjectAnimator rotationAnimator = ObjectAnimator.ofFloat(this, "rotationAngle", 0, -45, 0, 30, 0);
            rotationAnimator.setDuration(1_000);
            rotationAnimator.start();
        }
    }

    public void setShouldAnimateSideButton(boolean shouldAnimate) {
        System.out.println("LLL, setShouldAnimateSideButton: " + shouldAnimate);
        this.shouldAnimateSideButton = shouldAnimate;
        invalidate();
    }

    public void setRotationAngle(float rotationAngle) {
        this.rotationAngle = rotationAngle;
        invalidate();
    }

    @SuppressLint("AnimatorKeep")
    public void setArrowOffsetY(float offsetY) {
        this.arrowOffsetY = offsetY;
        invalidate();
    }
}
