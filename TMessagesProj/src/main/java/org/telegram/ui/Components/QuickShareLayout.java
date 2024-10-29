package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.telegram.tgnet.TLRPC;

public class QuickShareLayout extends FrameLayout {

	private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();

	protected int currentAccount = UserConfig.selectedAccount;

	private final BaseFragment fragment;
	private final Delegate delegate;

	private ArrayList<DialogCell> cells = new ArrayList<>();
	private DialogCell selectedCell;
	private View rootBackground, rectBackground;

	private final ArrayList<MessageObject> messages;
	private final float sideButtonStartX;

	private final long animationDuration = 300;
	private final long animationAlphaDuration = 300;

	public boolean canInteractive;
	private boolean sending;

	private FrameLayout nameToHideContainer, nameToShowContainer;
	private TextView nameToShow;

	private final Handler handler = new Handler();
	private Runnable longPressRunnable;
	private boolean isLongPress;

	private boolean pointingNoCell = true;
	private boolean touchFromView = false;
	private boolean deselectedCells = false;

	private final int maxDialogs = 5;
	private final int rectHeight = 64;
	private final int rectHorizontalPadding = 8;
	private final int rectRightOffset = 8;
	private final int verticalMargin = 8;
	private final int dialogSize = rectHeight - verticalMargin * 2;
	private final int paddingBetween = 10;
	private final float scaleFactor = 1.1f;

	private float sideButtonXDiff;
	private int rectWidth;
	private boolean displayAtSideButtonXCenter;

	public interface Delegate {
		void onOutsideClick(boolean withFade);

		void showHint(long did);

		void setAllowParentScroll();
	}

	public QuickShareLayout(BaseFragment fragment, Context context, Delegate delegate, int cellBottomY,
							float sideButtonStartX, ArrayList<MessageObject> messages) {
		super(context);

		this.fragment = fragment;
		this.delegate = delegate;
		this.messages = messages;
		this.sideButtonStartX = sideButtonStartX;

        fetchDialogs();
		initViews(context, cellBottomY);
	}

	public void show() {
		animateRect();
		animateCells();
	}

	@SuppressLint("ClickableViewAccessibility")
    private void initViews(Context context, int cellBottomY) {
		rootBackground = new View(context);
		GradientDrawable rootBackgroundDrawable = new GradientDrawable();
		rootBackgroundDrawable.setColor(0x00f0f0f0);
		rootBackground.setBackground(rootBackgroundDrawable);
		rootBackground.setOnTouchListener((v, event) -> {
			delegate.onOutsideClick(true);
			return false;
		});
		addView(rootBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 0, 0, 0, 0));

		rectBackground = new View(context);
		GradientDrawable backgroundDrawable = new GradientDrawable();
		backgroundDrawable.setCornerRadius(AndroidUtilities.dp((float) rectHeight / 2));
		backgroundDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
		rectBackground.setBackground(backgroundDrawable);

		LayoutParams layoutParams = new LayoutParams(
				AndroidUtilities.dp(dialogSize * dialogs.size() + rectHorizontalPadding*2 + paddingBetween*(dialogs.size()-1)),
				AndroidUtilities.dp(rectHeight),
				Gravity.END | Gravity.TOP
		);
		layoutParams.topMargin = cellBottomY - AndroidUtilities.dp(rectHeight + 56);

		rectWidth = AndroidUtilities.dp(dialogSize*dialogs.size() + rectHorizontalPadding*2 + paddingBetween*(dialogs.size()-1));
		int screenWidth = getResources().getDisplayMetrics().widthPixels;

		sideButtonXDiff = screenWidth - sideButtonStartX;
		if (sideButtonXDiff - AndroidUtilities.dp(rectRightOffset*2) < (float) rectWidth/2 + AndroidUtilities.dp(rectRightOffset)) {
			displayAtSideButtonXCenter = false;
			layoutParams.rightMargin = AndroidUtilities.dp(rectRightOffset);
		} else {
			displayAtSideButtonXCenter = true;
			layoutParams.rightMargin = (int) sideButtonXDiff - rectWidth/2 - AndroidUtilities.dp(16);
		}
		addView(rectBackground, layoutParams);

		Collections.reverse(dialogs);
		for (int i = 0; i < dialogs.size(); i++) {
			DialogCell cell = new DialogCell(context);
			cell.setDialog(dialogs.get(i).id);
			cells.add(cell);

			LayoutParams cellLayoutParams = new LayoutParams(
					AndroidUtilities.dp(dialogSize),
					LayoutHelper.WRAP_CONTENT,
					Gravity.END | Gravity.TOP
			);
			cellLayoutParams.topMargin = cellBottomY - AndroidUtilities.dp(rectHeight + 56 - verticalMargin);

			int offset = (i != 0) ? i * (dialogSize + paddingBetween) : 0;
			if (!displayAtSideButtonXCenter) {
				offset += rectRightOffset + rectHorizontalPadding;
			}
			int center = (int) sideButtonXDiff - rectWidth / 2 - AndroidUtilities.dp(rectHorizontalPadding);
			cellLayoutParams.rightMargin = AndroidUtilities.dp(offset) + (displayAtSideButtonXCenter ? center : 0);

			cell.setVisibility(View.INVISIBLE);
			addView(cell, cellLayoutParams);
		}
	}

	private void animateRect() {
		rectBackground.setVisibility(View.INVISIBLE);

		if (!displayAtSideButtonXCenter) {
			rectBackground.setX(-sideButtonXDiff + ((float) (rectWidth + rectRightOffset) / 2) + rectRightOffset + AndroidUtilities.dp(16));
		}

		ObjectAnimator moveDown = ObjectAnimator.ofFloat(rectBackground, "translationY", AndroidUtilities.dp(rectHeight));
		moveDown.setDuration(0);
		moveDown.start();

		ObjectAnimator scaleX = ObjectAnimator.ofFloat(rectBackground, "scaleX", 0f, 1f);
		ObjectAnimator scaleY = ObjectAnimator.ofFloat(rectBackground, "scaleY", 0f, 1f);

		ObjectAnimator translateX = ObjectAnimator.ofFloat(rectBackground, "translationX", 0f);
		ObjectAnimator translateY = ObjectAnimator.ofFloat(rectBackground, "translationY", 0f);

		scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
		scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
		translateX.setInterpolator(new AccelerateDecelerateInterpolator());
		translateY.setInterpolator(new AccelerateDecelerateInterpolator());

		scaleX.setDuration(animationDuration);
		scaleY.setDuration(animationDuration);
		translateX.setDuration(animationDuration);
		translateY.setDuration(animationDuration);

		AnimatorSet animatorSet = new AnimatorSet();
		animatorSet.playTogether(scaleX, scaleY, translateX, translateY);

		rectBackground.setVisibility(View.VISIBLE);
		animatorSet.start();
		animatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				super.onAnimationEnd(animation);
				canInteractive = true;
			}
		});
	}

	private void animateCells() {
		for (int i=0; i<cells.size(); i++) {
			DialogCell cell = cells.get(i);
			cell.setVisibility(View.INVISIBLE);

			ObjectAnimator moveDown = ObjectAnimator.ofFloat(cell, "translationY", AndroidUtilities.dp(rectHeight));
			moveDown.setDuration(0);
			moveDown.start();

			ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 0f, 1f, 1.075f, 1f);
			ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 0f, 1f, 1.075f, 1f);

			ObjectAnimator translateX = ObjectAnimator.ofFloat(cell, "translationX", 0f);
			ObjectAnimator translateY = ObjectAnimator.ofFloat(cell, "translationY", 0f);

			scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
			scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
			translateX.setInterpolator(new AccelerateDecelerateInterpolator());
			translateY.setInterpolator(new AccelerateDecelerateInterpolator());

			scaleX.setDuration(animationDuration);
			scaleY.setDuration(animationDuration);
			translateX.setDuration(animationDuration);
			translateY.setDuration(animationDuration);

			long delay = getStartDelayForCell(cells.size(), i);
			scaleX.setStartDelay(delay);
			scaleY.setStartDelay(delay);

			AnimatorSet animatorSet = new AnimatorSet();
			animatorSet.playTogether(translateX, translateY);

			AndroidUtilities.runOnUIThread(() -> {
				ObjectAnimator startScaleX = ObjectAnimator.ofFloat(cell, "scaleX", 0f);
				ObjectAnimator startScaleY = ObjectAnimator.ofFloat(cell, "scaleY", 0f);
				startScaleX.setDuration(0);
				startScaleY.setDuration(0);
				AnimatorSet animatorSet1 = new AnimatorSet();
				animatorSet1.playTogether(startScaleX, startScaleY);
				animatorSet1.start();

				AnimatorSet animatorSet2 = new AnimatorSet();
				animatorSet2.playTogether(scaleX, scaleY);
				cell.setVisibility(View.VISIBLE);
				animatorSet2.start();
			}, (long) (animationDuration / 3.5));
			animatorSet.start();
		}
	}

	private long getStartDelayForCell(int cellCount, int index) {
		if (cellCount == 5) {
			if (index == 2) {
				return (long) (animationDuration * (7f / 100));
			} else if (index == 1 || index == 3) {
				return (long) (animationDuration * (14.5f / 100));
			} else if (index == 0 || index == 4) {
				return animationDuration / 3;
			}
		} else if (cellCount == 4) {
			if (index == 1 || index == 2) {
				return (long) (animationDuration * (7f / 100));
			} else if (index == 0 || index == 3) {
				return animationDuration / 3;
			}
		} else if (cellCount == 3) {
			if (index == 1) {
				return (long) (animationDuration * (7f / 100));
			} else if (index == 0 || index == 2) {
				return animationDuration / 3;
			}
		}
		return (long) (animationDuration * (14.5f / 100));
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return checkTouches(event);
	}

	public boolean checkTouches(MotionEvent event) {
		if (sending) {
			return false;
		}

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				isLongPress = false;
				touchFromView = true;

				final float x = event.getRawX();
				final float y = event.getRawY();

				longPressRunnable = () -> {
                    isLongPress = true;
                    handleLongPress(x, y);
                };
				handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
				break;
			case MotionEvent.ACTION_MOVE:
				for (DialogCell cell : cells) {
					if (isPointInsideView(event.getRawX(), event.getRawY(), cell)) {
						if (selectedCell != cell) {
							selectedCell = cell;
							onCellHovered();
						}
						pointingNoCell = false;
						deselectedCells = false;
						break;
					} else {
						pointingNoCell = true;
					}
				}
				if (pointingNoCell) {
					if (!deselectedCells) {
						deselectCells();
						deselectedCells = true;
					}
				}
				break;
			case MotionEvent.ACTION_UP:
				handler.removeCallbacks(longPressRunnable);
				if (selectedCell != null) {
					sendInternal(cells.indexOf(selectedCell));
					selectedCell = null;
				} else {
					delegate.onOutsideClick(true);
				}
				isLongPress = false;
				break;
			case MotionEvent.ACTION_CANCEL:
				isLongPress = false;
				break;
		}
		return true;
	}

	private void onCellHovered() {
		for (DialogCell cell : cells) {
			if (cell == selectedCell) {
				cell.animate()
						.alpha(1f)
						.scaleX(scaleFactor)
						.scaleY(scaleFactor)
						.setDuration(animationAlphaDuration)
						.setInterpolator(new AccelerateDecelerateInterpolator())
						.start();
			} else {
				cell.animate()
						.alpha(0.5f)
						.scaleX(1f)
						.scaleY(1f)
						.setDuration(animationAlphaDuration)
						.setInterpolator(new AccelerateDecelerateInterpolator())
						.start();
			}
		}
		if (!touchFromView && !isLongPress) {
			createTextViewForCell(selectedCell);
		}
	}

	private void handleLongPress(float x, float y) {
		for (DialogCell cell : cells) {
			if (isPointInsideView(x, y, cell)) {
				selectedCell = cell;
				createTextViewForCell(selectedCell);
				touchFromView = false;
				isLongPress = false;
				break;
			}
		}
	}

	private void deselectCells() {
		if (selectedCell != null) {
			selectedCell.animate()
					.alpha(1f)
					.scaleX(1f)
					.scaleY(1f)
					.setDuration(animationAlphaDuration)
					.setInterpolator(new AccelerateDecelerateInterpolator())
					.start();
		}
		selectedCell = null;
		if (nameToShowContainer != null) {
			nameToShowContainer.animate()
					.alpha(0f)
					.scaleX(0f)
					.scaleY(0f)
					.setDuration(animationAlphaDuration)
					.setInterpolator(new AccelerateDecelerateInterpolator())
					.withEndAction(() -> {
						removeView(nameToShowContainer);
						nameToShowContainer = null;
					})
					.start();
		}
		for (DialogCell cell : cells) {
			cell.animate()
					.alpha(1f)
					.scaleX(1f)
					.scaleY(1f)
					.setDuration(animationAlphaDuration)
					.setInterpolator(new AccelerateDecelerateInterpolator())
					.start();
		}
	}

	private void createTextViewForCell(DialogCell cell) {
		if (sending) {
			return;
		}
		if (nameToShowContainer == null) {
			createAndShowTextView(cell);
		} else {
			nameToHideContainer = nameToShowContainer;
			nameToHideContainer.animate()
					.alpha(0f)
					.scaleX(0f)
					.scaleY(0f)
					.setDuration(animationAlphaDuration)
					.setInterpolator(new AccelerateDecelerateInterpolator())
					.withEndAction(() -> {
						removeView(nameToHideContainer);
						nameToHideContainer = null;
					})
					.start();

			createAndShowTextView(cell);
		}
	}

	private FrameLayout createContainerForTv(DialogCell cell) {
		nameToShowContainer = new FrameLayout(getContext());
		LayoutParams containerParams = new LayoutParams(
				LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.END
		);

		GradientDrawable backgroundDrawable = new GradientDrawable();
		backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
		backgroundDrawable.setCornerRadius(AndroidUtilities.dp(30));
		backgroundDrawable.setColor(Color.argb(156, 60, 60, 60));
		nameToShowContainer.setBackground(backgroundDrawable);

		int[] location = new int[2];
		cell.getLocationOnScreen(location);

		nameToShow.measure(0, 0);
		int textViewWidth = nameToShow.getMeasuredWidth();

		int cellIndex = cells.indexOf(cell);
		int offset = (cellIndex != 0) ? cellIndex * (dialogSize + paddingBetween) : 0;
		int centerOffset = (AndroidUtilities.dp((float) dialogSize/2) - textViewWidth) / 2;
		int totalOffset;

		if (displayAtSideButtonXCenter) {
			int center = (int) sideButtonXDiff - rectWidth / 2 + AndroidUtilities.dp((float) rectHorizontalPadding / 2);
			totalOffset = AndroidUtilities.dp(offset) + center + centerOffset;
		} else {
			offset += rectRightOffset + rectHorizontalPadding;
			centerOffset = (AndroidUtilities.dp(dialogSize) - textViewWidth) / 2;
			totalOffset = AndroidUtilities.dp(offset) + centerOffset;
		}

		if (totalOffset < AndroidUtilities.dp(rectRightOffset)) {
			totalOffset = AndroidUtilities.dp(rectRightOffset);
		}
		containerParams.rightMargin = totalOffset;
		containerParams.topMargin = location[1] - cell.getHeight() + AndroidUtilities.dp(8);
		addView(nameToShowContainer, containerParams);

		return nameToShowContainer;
	}

	private void createAndShowTextView(DialogCell cell) {
		nameToShow = new androidx.appcompat.widget.AppCompatTextView(getContext()) {
			@Override
			public void setText(CharSequence text, BufferType type) {
				text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), dp(10), false);
				super.setText(text, type);
			}
		};

		nameToShow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		nameToShow.setMaxLines(1);
		nameToShow.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		nameToShow.setLines(1);
		nameToShow.setEllipsize(TextUtils.TruncateAt.END);
		nameToShow.setIncludeFontPadding(false);
		nameToShow.setTypeface(Typeface.DEFAULT_BOLD);

		nameToShow.setText(cell.getName());
		nameToShow.setTextColor(Color.WHITE);
		nameToShow.setPadding(AndroidUtilities.dp(10), 6, AndroidUtilities.dp(10), 6);

		LayoutParams params = new LayoutParams(
				LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.END
		);

		FrameLayout container = createContainerForTv(cell);
		container.addView(nameToShow, params);

		container.setAlpha(0f);
		container.setScaleX(0f);
		container.setScaleY(0f);
		container.animate()
				.alpha(1f)
				.scaleX(1f)
				.scaleY(1f)
				.setDuration(animationAlphaDuration)
				.setInterpolator(new AccelerateDecelerateInterpolator())
				.start();
	}

	private void sendInternal(int index) {
		TLRPC.Dialog dialog = dialogs.get(index);
		sending = true;

		removeView(rootBackground);
		delegate.setAllowParentScroll();

		if (AlertsCreator.checkSlowMode(getContext(), currentAccount, dialog.id, false)) {
			fadeOut(true);
			return;
		}

		int result = SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, dialog.id, false,false, true, 0, null);
		AlertsCreator.showSendMediaAlert(result, fragment, null);

		if (result != 0) {
			fadeOut(true);
			return;
		}

		boolean isSaved = dialog.id == UserConfig.getInstance(currentAccount).clientUserId;
		fadeOut(isSaved && UserConfig.getInstance(UserConfig.selectedAccount).isPremium());
		delegate.showHint(dialog.id);
		animateDialogTransition(isSaved);
	}

	private void animateDialogTransition(boolean isSaved) {
		selectedCell.bringToFront();

		int[] location = new int[2];
		selectedCell.getLocationOnScreen(location);

		float currentX = location[0];
		float currentY = location[1];

		int index = cells.indexOf(selectedCell) + 1;

		if (isLandscapeOrientation()) {
			currentX -= AndroidUtilities.navigationBarHeight;
		}
		float x1 = currentX;
		if (isSaved) {
			x1 -= AndroidUtilities.dp(100);
		} else {
			int factor = 200 - index * 25;
			x1 -= AndroidUtilities.dp(factor);
		}
		float y1 = currentY - AndroidUtilities.dp(dialogSize * 1.4f);
		float x2 = AndroidUtilities.dp(14);
		float centerOffset = 51 + 8 + 10 + 15 + (float) dialogSize / 2; // 51 - button height, 8 - margin, 10 - padding, 15 - half of icon
		float y2 = getHeight() - AndroidUtilities.dp(centerOffset);
		if (isSaved && UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
			y2 = AndroidUtilities.dp(centerOffset);
		}

		Path path = new Path();
		path.moveTo(currentX, currentY);
		path.quadTo(x1, y1, x2, y2);

		ObjectAnimator pathAnimator = ObjectAnimator.ofFloat(selectedCell, View.X, View.Y, path);
		pathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
		pathAnimator.setDuration(animationDuration);

		ObjectAnimator scaleX = ObjectAnimator.ofFloat(selectedCell, "scaleX", 1f, 0.5f);
		ObjectAnimator scaleY = ObjectAnimator.ofFloat(selectedCell, "scaleY", 1f, 0.5f);

		ObjectAnimator alpha = ObjectAnimator.ofFloat(selectedCell, "alpha", 1f, 0f);;
		if (isSaved && UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
			alpha = ObjectAnimator.ofFloat(selectedCell, "alpha", 0f);
		}
		alpha.setStartDelay((long) (animationDuration * 0.75f));

		scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
		scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
		alpha.setInterpolator(new AccelerateDecelerateInterpolator());

		scaleX.setDuration(animationDuration);
		scaleY.setDuration(animationDuration);
		alpha.setDuration(animationDuration);

		AnimatorSet animatorSet = new AnimatorSet();
		animatorSet.playTogether(pathAnimator, scaleX, scaleY, alpha);
		animatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(android.animation.Animator animation) {
				delegate.onOutsideClick(true);
			}
		});
		animatorSet.start();
	}

	private void fadeOut(boolean withSelectedDialog) {
		AnimatorSet animatorSet = new AnimatorSet();
		List<Animator> animators = new ArrayList<>();
		ArrayList<View> cellsToFade = new ArrayList<>();

		for (DialogCell cell : cells) {
			if (cell != selectedCell) {
				cellsToFade.add(cell);
			}
		}
		if (withSelectedDialog) {
			cellsToFade.add(selectedCell);
		}

		cellsToFade.add(nameToHideContainer);
		cellsToFade.add(nameToShowContainer);

		for (View cell : cellsToFade) {
			ObjectAnimator fadeOut = ObjectAnimator.ofFloat(cell, "alpha", 0f);
			fadeOut.setDuration(animationDuration);
			animators.add(fadeOut);
		}

		ObjectAnimator fadeOut = ObjectAnimator.ofFloat(rectBackground, "alpha", 0f);
		fadeOut.setDuration(animationDuration);
		animators.add(fadeOut);

		animatorSet.playTogether(animators);
		animatorSet.start();
	}

	private void fetchDialogs() {
		dialogs.clear();
		long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
		if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
			TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
			dialogs.add(dialog);
		}
		ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
		for (int a = 0; a < allDialogs.size(); a++) {
			TLRPC.Dialog dialog = allDialogs.get(a);
			if (!(dialog instanceof TLRPC.TL_dialog)) {
				continue;
			}
			if (dialog.id == selfUserId) {
				continue;
			}
			if (!DialogObject.isEncryptedDialog(dialog.id)) {
				if (DialogObject.isUserDialog(dialog.id)) {
					dialogs.add(dialog);
				} else {
					TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
					if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
						dialogs.add(dialog);
					}
				}
			}
		}
		dialogs = new ArrayList<TLRPC. Dialog>(dialogs.subList(0, Math.min(dialogs.size(), maxDialogs)));
	}

	private boolean isPointInsideView(float x, float y, View view) {
		int[] location = new int[2];
		view.getLocationOnScreen(location);
		int viewX = location[0];
		int viewY = location[1];

		return (x > viewX && x < (viewX + view.getWidth()) &&
				y > viewY && y < (viewY + view.getHeight()));
	}

	private boolean isLandscapeOrientation() {
		return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}

	class DialogCell extends FrameLayout {

		private final BackupImageView imageView;
		private final AvatarDrawable avatarDrawable = new AvatarDrawable() {
			@Override
			public void invalidateSelf() {
				super.invalidateSelf();
				imageView.invalidate();
			}
		};

		private CharSequence name;

		public DialogCell(Context context) {
			super(context);
			setWillNotDraw(false);

			imageView = new BackupImageView(context);
			imageView.setRoundRadius(dp((float) dialogSize / 2));
			addView(imageView, LayoutHelper.createFrame(dialogSize, dialogSize, Gravity.CENTER, 0, 0, 0, 0));
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(dialogSize), MeasureSpec.EXACTLY));
		}

		public void setDialog(long did) {
			if (DialogObject.isUserDialog(did)) {
				TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
				invalidate();
				avatarDrawable.setInfo(currentAccount, user);
				if (UserObject.isReplyUser(user)) {
					avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
					imageView.setImage(null, null, avatarDrawable, user);
				} else if (UserObject.isUserSelf(user)) {
					name = LocaleController.getString(R.string.SavedMessages);
					avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
					imageView.setImage(null, null, avatarDrawable, user);
				} else {
					if (user != null) {
						name = ContactsController.formatName(user.first_name, user.last_name);
					} else {
						name = "";
					}
					imageView.setForUserOrChat(user, avatarDrawable);
				}
				imageView.setRoundRadius(dp((float) dialogSize / 2));
			} else {
				TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
				if (chat != null) {
					name = chat.title;
				} else {
					name = "";
				}
				avatarDrawable.setInfo(currentAccount, chat);
				imageView.setForUserOrChat(chat, avatarDrawable);
				imageView.setRoundRadius(chat != null && chat.forum ? dp(16) : dp((float) dialogSize / 2));
			}
		}

		public BackupImageView getImageView() {
			return imageView;
		}

		public CharSequence getName() {
			return name;
		}
	}
}
