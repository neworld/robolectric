package org.robolectric.shadows;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.R;
import org.robolectric.Robolectric;
import org.robolectric.TestRunners;
import org.robolectric.res.Attribute;
import org.robolectric.res.EmptyResourceLoader;
import org.robolectric.tester.android.view.TestWindow;
import org.robolectric.util.TestAnimationListener;
import org.robolectric.util.TestOnClickListener;
import org.robolectric.util.TestOnLongClickListener;
import org.robolectric.util.TestRunnable;
import org.robolectric.util.Transcript;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertEquals;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.robolectric.Robolectric.*;

@RunWith(TestRunners.WithDefaults.class)
public class ViewTest {
    private View view;
    private Transcript transcript;

    @Before
    public void setUp() throws Exception {
        transcript = new Transcript();
        view = new View(application);
    }

    @Test
    public void testHasNullLayoutParamsUntilAddedToParent() throws Exception {
        assertThat(view.getLayoutParams()).isNull();
        new LinearLayout(application).addView(view);
        assertThat(view.getLayoutParams()).isNotNull();
    }

    @Test
    public void layout_shouldAffectWidthAndHeight() throws Exception {
        assertThat(view.getWidth()).isEqualTo(0);
        assertThat(view.getHeight()).isEqualTo(0);

        view.layout(100, 200, 303, 404);
        assertThat(view.getWidth()).isEqualTo(303 - 100);
        assertThat(view.getHeight()).isEqualTo(404 - 200);
    }

    @Test
    public void measuredDimensions() throws Exception {
        View view1 = new View(Robolectric.application) {
            {
                setMeasuredDimension(123, 456);
            }
        };
        assertThat(view1.getMeasuredWidth()).isEqualTo(123);
        assertThat(view1.getMeasuredHeight()).isEqualTo(456);
    }

    @Test
    public void layout_shouldCallOnLayoutOnlyIfChanged() throws Exception {
        View view1 = new View(Robolectric.application) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                transcript.add("onLayout " + changed + " " + left + " " + top + " " + right + " " + bottom);
            }

            @Override
            public void invalidate() {
                transcript.add("invalidate");
            }
        };
        view1.layout(0, 0, 0, 0);
        transcript.assertNoEventsSoFar();
        view1.layout(1, 2, 3, 4);
        transcript.assertEventsSoFar("invalidate", "onLayout true 1 2 3 4");
        view1.layout(1, 2, 3, 4);
        transcript.assertNoEventsSoFar();
    }

    @Test
    public void shouldFocus() throws Exception {
        final Transcript transcript = new Transcript();

        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                transcript.add(hasFocus ? "Gained focus" : "Lost focus");
            }
        });

        assertFalse(view.isFocused());
        assertFalse(view.hasFocus());
        transcript.assertNoEventsSoFar();

        view.requestFocus();
        assertTrue(view.isFocused());
        assertTrue(view.hasFocus());
        transcript.assertEventsSoFar("Gained focus");

        view.clearFocus();
        assertFalse(view.isFocused());
        assertFalse(view.hasFocus());
        transcript.assertEventsSoFar("Lost focus");
    }

    @Test
    public void shouldNotBeFocusableByDefault() throws Exception {
        assertFalse(view.isFocusable());

        view.setFocusable(true);
        assertTrue(view.isFocusable());
    }

    @Test
    public void shouldKnowIfThisOrAncestorsAreVisible() throws Exception {
        assertTrue(view.isShown());

        ViewGroup parent = new LinearLayout(Robolectric.application);
        parent.addView(view);

        ViewGroup grandParent = new LinearLayout(Robolectric.application);
        grandParent.addView(parent);

        grandParent.setVisibility(View.GONE);

        assertFalse(view.isShown());
    }

    @Test
    public void shouldInflateMergeRootedLayoutAndNotCreateReferentialLoops() throws Exception {
        LinearLayout root = new LinearLayout(Robolectric.application);
        root.inflate(new Activity(), R.layout.inner_merge, root);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            assertNotSame(root, child);
        }
    }

    @Test
    public void performLongClick_shouldClickOnView() throws Exception {
        TestOnLongClickListener clickListener = new TestOnLongClickListener();
        view.setOnLongClickListener(clickListener);
        shadowOf(view).performLongClick();

        assertTrue(clickListener.clicked);
    }

    @Test
    public void checkedClick_shouldClickOnView() throws Exception {
        TestOnClickListener clickListener = new TestOnClickListener();
        view.setOnClickListener(clickListener);
        shadowOf(view).checkedPerformClick();

        assertTrue(clickListener.clicked);
    }

    @Test(expected = RuntimeException.class)
    public void checkedClick_shouldThrowIfViewIsNotVisible() throws Exception {
        ViewGroup grandParent = new LinearLayout(Robolectric.application);
        ViewGroup parent = new LinearLayout(Robolectric.application);
        grandParent.addView(parent);
        parent.addView(view);
        grandParent.setVisibility(View.GONE);

        shadowOf(view).checkedPerformClick();
    }

    @Test(expected = RuntimeException.class)
    public void checkedClick_shouldThrowIfViewIsDisabled() throws Exception {
        view.setEnabled(false);
        shadowOf(view).checkedPerformClick();
    }

    @Test
    public void getBackground_shouldReturnNullIfNoBackgroundHasBeenSet() throws Exception {
        assertThat(view.getBackground()).isNull();
    }

    @Test
    public void shouldSetBackgroundColor() {
        int red = 0xffff0000;
        view.setBackgroundColor(red);
        assertThat((ColorDrawable) view.getBackground()).isEqualTo(new ColorDrawable(red));
    }

    @Test
    public void shouldSetBackgroundResource() throws Exception {
        view.setBackgroundResource(R.drawable.an_image);
        assertThat(view.getBackground()).isEqualTo(view.getResources().getDrawable(R.drawable.an_image));
        assertThat(shadowOf(view).getBackgroundResourceId()).isEqualTo(R.drawable.an_image);
    }

    @Test
    public void shouldClearBackgroundResource() throws Exception {
        view.setBackgroundResource(R.drawable.an_image);
        view.setBackgroundResource(0);
        assertThat(view.getBackground()).isEqualTo(null);
        assertThat(shadowOf(view).getBackgroundResourceId()).isEqualTo(0);
    }

    @Test
    public void shouldRecordBackgroundColor() {
        int[] colors = {R.color.black, R.color.clear, R.color.white};

        for (int color : colors) {
            view.setBackgroundColor(color);
            assertThat(shadowOf(view).getBackgroundColor()).isEqualTo(color);
        }
    }

    @Test
    public void shouldRecordBackgroundDrawable() {
        Drawable drawable = new BitmapDrawable(BitmapFactory.decodeFile("some/fake/file"));
        view.setBackgroundDrawable(drawable);
        assertThat(view.getBackground()).isSameAs(drawable);
        assertThat(visualize(view)).isEqualTo("background:\nBitmap for file:some/fake/file");
    }

    @Test
    public void shouldPostActionsToTheMessageQueue() throws Exception {
        Robolectric.pauseMainLooper();

        TestRunnable runnable = new TestRunnable();
        view.post(runnable);
        assertFalse(runnable.wasRun);

        Robolectric.unPauseMainLooper();
        assertTrue(runnable.wasRun);
    }

    @Test
    public void shouldPostInvalidateDelayed() throws Exception {
        Robolectric.pauseMainLooper();

        view.postInvalidateDelayed(100);
        ShadowView shadowView = shadowOf(view);
        assertFalse(shadowView.wasInvalidated());

        Robolectric.unPauseMainLooper();
        assertTrue(shadowView.wasInvalidated());
    }

    @Test
    public void shouldPostActionsToTheMessageQueueWithDelay() throws Exception {
        Robolectric.pauseMainLooper();

        TestRunnable runnable = new TestRunnable();
        view.postDelayed(runnable, 1);
        assertFalse(runnable.wasRun);

        Robolectric.getUiThreadScheduler().advanceBy(1);
        assertTrue(runnable.wasRun);
    }

    @Test
    public void shouldSupportAllConstructors() throws Exception {
        new View(Robolectric.application);
        new View(Robolectric.application, null);
        new View(Robolectric.application, null, 0);
    }

    @Test
    public void shouldRememberIsPressed() {
        view.setPressed(true);
        assertTrue(view.isPressed());
        view.setPressed(false);
        assertFalse(view.isPressed());
    }

    @Test
    public void shouldAddOnClickListenerFromAttribute() throws Exception {
      RoboAttributeSet attrs = new RoboAttributeSet(new ArrayList<Attribute>(), new EmptyResourceLoader(), null);
      attrs.put("android:attr/onClick", "clickMe", R.class.getPackage().getName());

      view = new View(Robolectric.application, attrs);
      assertNotNull(shadowOf(view).getOnClickListener());
    }

    @Test
    public void shouldCallOnClickWithAttribute() throws Exception {
      final AtomicBoolean called = new AtomicBoolean(false);
      Activity context = new Activity() {
        @SuppressWarnings("UnusedDeclaration")
        public void clickMe(View view) {
          called.set(true);
        }
      };
      RoboAttributeSet attrs = new RoboAttributeSet(new ArrayList<Attribute>(), new EmptyResourceLoader(), null);
      attrs.put("android:attr/onClick", "clickMe", R.class.getPackage().getName());

      view = new View(context, attrs);
      view.performClick();
      assertTrue("Should have been called", called.get());
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionWithBadMethodName() throws Exception {
      final AtomicBoolean called = new AtomicBoolean(false);
      Activity context = new Activity() {
        public void clickMe(View view) {
          called.set(true);
        }
      };
      RoboAttributeSet attrs = new RoboAttributeSet(new ArrayList<Attribute>(), null, null);
      attrs.put("android:onClick", "clickYou", R.class.getPackage().getName());

      view = new View(context, attrs);
      view.performClick();
    }

    @Test
    public void shouldSetAnimation() throws Exception {
        Animation anim = new TestAnimation();
        view.setAnimation(anim);
        assertThat(view.getAnimation()).isSameAs(anim);
    }

    @Test
    public void shouldStartAndClearAnimation() throws Exception {
        Animation anim = new TestAnimation();
        TestAnimationListener listener = new TestAnimationListener();
        anim.setAnimationListener(listener);
        assertThat(listener.wasStartCalled).isFalse();
        assertThat(listener.wasRepeatCalled).isFalse();
        assertThat(listener.wasEndCalled).isFalse();
        view.startAnimation(anim);
        assertThat(listener.wasStartCalled).isTrue();
        assertThat(listener.wasRepeatCalled).isFalse();
        assertThat(listener.wasEndCalled).isFalse();
        view.clearAnimation();
        assertThat(listener.wasStartCalled).isTrue();
        assertThat(listener.wasRepeatCalled).isFalse();
        assertThat(listener.wasEndCalled).isTrue();
    }

    @Test
    public void shouldFindViewWithTag() {
        view.setTag("tagged");
        assertThat(view.findViewWithTag("tagged")).isSameAs(view);
    }

    @Test
    public void shouldFindViewWithTag_whenViewOverridesGetTag() throws Exception {
        View view = new View(Robolectric.application) {
            @Override
            public Object getTag() {
                return "blarg";
            }
        };
        assertThat(view.findViewWithTag("blarg")).isSameAs(view);
    }

    @Test
    public void scrollTo_shouldStoreTheScrolledCoordinates() throws Exception {
        view.scrollTo(1, 2);
        assertThat(shadowOf(view).scrollToCoordinates).isEqualTo(new Point(1, 2));
    }

    @Test
    public void shouldScrollTo() throws Exception {
        view.scrollTo(7, 6);

        assertEquals(7, view.getScrollX());
        assertEquals(6, view.getScrollY());
    }

    @Test
    public void shouldGetScrollXAndY() {
        assertEquals(0, view.getScrollX());
        assertEquals(0, view.getScrollY());
    }

    @Test
    public void getViewTreeObserver_shouldReturnTheSameObserverFromMultipleCalls() throws Exception {
        ViewTreeObserver observer = view.getViewTreeObserver();
        assertThat(observer).isInstanceOf(ViewTreeObserver.class);
        assertThat(view.getViewTreeObserver()).isSameAs(observer);
    }

    @Test
    public void dispatchTouchEvent_sendsMotionEventToOnTouchEvent() throws Exception {
        TouchableView touchableView = new TouchableView(Robolectric.application);
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 12f, 34f, 0);
        touchableView.dispatchTouchEvent(event);
        assertThat(touchableView.event).isSameAs(event);
        view.dispatchTouchEvent(event);
        assertThat(shadowOf(view).getLastTouchEvent()).isSameAs(event);
    }

    @Test
    public void dispatchTouchEvent_listensToTrueFromListener() throws Exception {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 12f, 34f, 0);
        view.dispatchTouchEvent(event);
        assertThat(shadowOf(view).getLastTouchEvent()).isNull();
    }

    @Test
    public void dispatchTouchEvent_listensToFalseFromListener() throws Exception {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return false;
            }
        });
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 12f, 34f, 0);
        view.dispatchTouchEvent(event);
        assertThat(shadowOf(view).getLastTouchEvent()).isSameAs(event);
    }

    @Test
    public void test_nextFocusDownId() throws Exception {
        assertEquals(View.NO_ID, view.getNextFocusDownId());

        view.setNextFocusDownId(R.id.icon);
        assertEquals(R.id.icon, view.getNextFocusDownId());
    }

    @Test
    public void dispatchOnAnimationEnd() throws Exception {
        TestView view1 = new TestView(new Activity());
        assertFalse(view1.onAnimationEndWasCalled);
        shadowOf(view1).finishedAnimation();
        assertTrue(view1.onAnimationEndWasCalled);
    }

    @Test
    public void test_measuredDimension() {
        // View does not provide its own onMeasure implementation
        TestView view1 = new TestView(new Activity());

        assertThat(view1.getHeight()).isEqualTo(0);
        assertThat(view1.getWidth()).isEqualTo(0);
        assertThat(view1.getMeasuredHeight()).isEqualTo(0);
        assertThat(view1.getMeasuredWidth()).isEqualTo(0);

        view1.measure(MeasureSpec.makeMeasureSpec(150, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(300, MeasureSpec.AT_MOST));

        assertThat(view1.getHeight()).isEqualTo(0);
        assertThat(view1.getWidth()).isEqualTo(0);
        assertThat(view1.getMeasuredHeight()).isEqualTo(300);
        assertThat(view1.getMeasuredWidth()).isEqualTo(150);
    }

    @Test
    public void test_measuredDimensionCustomView() {
        // View provides its own onMeasure implementation
        TestView2 view2 = new TestView2(new Activity());

        assertThat(view2.getHeight()).isEqualTo(0);
        assertThat(view2.getWidth()).isEqualTo(0);
        assertThat(view2.getMeasuredHeight()).isEqualTo(0);
        assertThat(view2.getMeasuredWidth()).isEqualTo(0);

        view2.measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(600, MeasureSpec.AT_MOST));

        assertThat(view2.getHeight()).isEqualTo(0);
        assertThat(view2.getWidth()).isEqualTo(0);
        assertThat(view2.getMeasuredHeight()).isEqualTo(400);
        assertThat(view2.getMeasuredWidth()).isEqualTo(800);
    }

    @Test
    public void shouldGetAndSetTranslations() throws Exception {
        view = new TestView(new Activity());
        view.setTranslationX(8.9f);
        view.setTranslationY(4.6f);

        assertThat(view.getTranslationX()).isEqualTo(8.9f);
        assertThat(view.getTranslationY()).isEqualTo(4.6f);
    }

    @Test
    public void shouldGetAndSetAlpha() throws Exception {
        view = new TestView(new Activity());
        view.setAlpha(9.1f);

        assertThat(view.getAlpha()).isEqualTo(9.1f);
    }

    @Test
    public void itKnowsIfTheViewIsShown() {
        view.setVisibility(View.VISIBLE);
        assertThat(view.isShown()).isTrue();
    }

    @Test
    public void itKnowsIfTheViewIsNotShown() {
        view.setVisibility(View.GONE);
        assertThat(view.isShown()).isFalse();

        view.setVisibility(View.INVISIBLE);
        assertThat(view.isShown()).isFalse();
    }

    @Test
    public void shouldTrackRequestLayoutCalls() throws Exception {
        assertThat(shadowOf(view).didRequestLayout()).isFalse();
        view.requestLayout();
        assertThat(shadowOf(view).didRequestLayout()).isTrue();
        shadowOf(view).setDidRequestLayout(false);
        assertThat(shadowOf(view).didRequestLayout()).isFalse();
    }

    public void shouldClickAndNotClick() throws Exception {
        assertThat(view.isClickable()).isFalse();
        view.setClickable(true);
        assertThat(view.isClickable()).isTrue();
        view.setClickable(false);
        assertThat(view.isClickable()).isFalse();
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ;
            }
        });
        assertThat(view.isClickable()).isTrue();
    }

    @Test
    public void shouldLongClickAndNotLongClick() throws Exception {
        assertThat(view.isLongClickable()).isFalse();
        view.setLongClickable(true);
        assertThat(view.isLongClickable()).isTrue();
        view.setLongClickable(false);
        assertThat(view.isLongClickable()).isFalse();
        view.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return false;
            }
        });
        assertThat(view.isLongClickable()).isTrue();
    }

    @Test
    public void setScaleX_shouldSetScaleX() throws Exception {
        assertThat(shadowOf(view).getScaleX()).isEqualTo(1f);
        shadowOf(view).setScaleX(2.5f);
        assertThat(shadowOf(view).getScaleX()).isEqualTo(2.5f);
        shadowOf(view).setScaleX(0.5f);
        assertThat(shadowOf(view).getScaleX()).isEqualTo(0.5f);
    }

    @Test
    public void setScaleY_shouldSetScaleY() throws Exception {
        assertThat(shadowOf(view).getScaleX()).isEqualTo(1f);
        shadowOf(view).setScaleY(2.5f);
        assertThat(shadowOf(view).getScaleY()).isEqualTo(2.5f);
        shadowOf(view).setScaleY(0.5f);
        assertThat(shadowOf(view).getScaleY()).isEqualTo(0.5f);
    }

    @Test
    public void performHapticFeedback_shouldSetLastPerformedHapticFeedback() throws Exception {
        assertThat(shadowOf(view).lastHapticFeedbackPerformed()).isEqualTo(-1);
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        assertThat(shadowOf(view).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.LONG_PRESS);
    }

    @Test
    public void canAssertThatSuperDotOnLayoutWasCalledFromViewSubclasses() throws Exception {
        TestView2 view = new TestView2(new Activity());
        assertThat(shadowOf(view).onLayoutWasCalled()).isFalse();
        view.onLayout(true, 1, 2, 3, 4);
        assertThat(shadowOf(view).onLayoutWasCalled()).isTrue();
    }

    @Test
    public void setScrolls_canBeAskedFor() throws Exception {
        view.setScrollX(234);
        view.setScrollY(544);
        assertThat(view.getScrollX()).isEqualTo(234);
        assertThat(view.getScrollY()).isEqualTo(544);
    }

    @Test
    public void setScrolls_firesOnScrollChanged() throws Exception {
        TestView testView = new TestView(new Activity());
        testView.setScrollX(122);
        testView.setScrollY(150);
        testView.setScrollX(453);
        assertThat(testView.oldl).isEqualTo(122);
        testView.setScrollY(54);
        assertThat(testView.l).isEqualTo(453);
        assertThat(testView.t).isEqualTo(54);
        assertThat(testView.oldt).isEqualTo(150);
    }

    private static class TestAnimation extends Animation {
    }

    private static class TouchableView extends View {
        MotionEvent event;

        public TouchableView(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            this.event = event;
            return false;
        }
    }

    public static class TestView extends View {
        boolean onAnimationEndWasCalled;
        private int l;
        private int t;
        private int oldl;
        private int oldt;

        public TestView(Context context) {
            super(context);
        }

        @Override
        protected void onAnimationEnd() {
            super.onAnimationEnd();
            onAnimationEndWasCalled = true;
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            this.l = l;
            this.t = t;
            this.oldl = oldl;
            this.oldt = oldt;
        }
    }

    private static class TestView2 extends View {
        public TestView2(Context context) {
            super(context);
        }

        @Override
        public void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(800, 400);
        }
    }
    
    @Test public void shouldCallOnAttachedToAndDetachedFromWindow() throws Exception {
        MyView parent = new MyView("parent");
        parent.addView(new MyView("child"));
        transcript.assertNoEventsSoFar();

        TestWindow window = new TestWindow(application);
        window.setContentView(parent);
        transcript.assertEventsSoFar("parent attached", "child attached");

        parent.addView(new MyView("another child"));
        transcript.assertEventsSoFar("another child attached");

        MyView temporaryChild = new MyView("temporary child");
        parent.addView(temporaryChild);
        transcript.assertEventsSoFar("temporary child attached");
        assertTrue(shadowOf(temporaryChild).isAttachedToWindow());

        parent.removeView(temporaryChild);
        transcript.assertEventsSoFar("temporary child detached");
        assertFalse(shadowOf(temporaryChild).isAttachedToWindow());

        window.setContentView(null);
        transcript.assertEventsSoFar("parent detached", "child detached", "another child detached");
    }

    @Test public void removeAllViews_shouldCallOnAttachedToAndDetachedFromWindow() throws Exception {
        MyView parent = new MyView("parent");
        parent.addView(new MyView("child"));
        parent.addView(new MyView("another child"));
        new TestWindow(application).setContentView(parent);
        transcript.clear();
        parent.removeAllViews();
        transcript.assertEventsSoFar("child detached", "another child detached");
    }

    private class MyView extends LinearLayout {
        private String name;

        public MyView(String name) {
            super(Robolectric.application);
            this.name = name;
        }

        @Override protected void onAttachedToWindow() {
            transcript.add(name + " attached");
            super.onAttachedToWindow();
        }

        @Override protected void onDetachedFromWindow() {
            transcript.add(name + " detached");
            super.onDetachedFromWindow();
        }
    }
}
