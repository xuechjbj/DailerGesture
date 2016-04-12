package touchtool.qeexo.com.twister;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;

/**
 * Created by chaojunxue on 4/6/16.
 */
public class TwisterKnobView extends View{

    private static final int GESTURE_POINTER_COUNTS = 4;

    //The ratio depend on the specific image. Need adjust it if we load new image.
    private static final float KnotRatio = 1.9f / 3f;

    private static String TAG = "xue";

    private Bitmap mKnobScaleImg;
    private Bitmap mRotateKnobImg;
    private boolean mVisibleKnob;
    private Paint mPaint;


    private float mRotatingDeg;
    HashMap<Integer, Point> mPrevTouchPt = new HashMap<Integer, Point>(GESTURE_POINTER_COUNTS*2);
    HashMap<Integer, Point> mMovingTouchPt = new HashMap<Integer, Point>(GESTURE_POINTER_COUNTS*2);

    public TwisterKnobView(Context context) {
        this(context, null, 0);
    }

    public TwisterKnobView(Context context, AttributeSet attrs) {

        this(context, attrs, 0);
    }

    public TwisterKnobView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mKnobScaleImg = BitmapFactory.decodeResource(getResources(), R.drawable.volume_scale);
        mRotateKnobImg = BitmapFactory.decodeResource(getResources(), R.drawable.volume_rotation_knob);;

        mVisibleKnob = false;
        mRotatingDeg = 0f;

        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextSize(40);
    }

    public void onDraw(Canvas canvas){
        super.onDraw(canvas);

        canvas.drawColor(Color.BLUE);

        canvas.drawText(getResources().getText(R.string.hint_msg).toString(), 10, 50, mPaint);

        if(mVisibleKnob) {
            drawKnobWidget(canvas, 500, 800, 0.5f);
        }
    }

    /*
     * Draw a rotatable knob widget. The fix part of the knob is stored is mKnobScaleImg. The
     * rotation part is in mRotateKnobImg.
     * Arguments:
     *     cx, cy - the center point of the widget in the canvas
     *     scale - the scale ratio of the knob widget
     */
    protected void drawKnobWidget(Canvas canvas, int cx, int cy, float scale){
        int canvasWidth = cx * 2;//canvas.getWidth();
        int canvasHeight = cy * 2;//canvas.getHeight();

        Matrix matrix = new Matrix();

        matrix.setScale(scale, scale);
        int centreX = (int)(canvasWidth - mKnobScaleImg.getWidth() * scale) / 2;
        int centreY = (int)(canvasHeight - mKnobScaleImg.getHeight() * scale) / 2;
        matrix.postTranslate(centreX, centreY);

        Matrix matrix1 = new Matrix();

        matrix1.setScale(KnotRatio * scale, KnotRatio * scale);
        int centreX1 = (canvasWidth - (int)(mRotateKnobImg.getWidth() * KnotRatio * scale)) / 2;
        int centreY1 = (canvasHeight - (int)(mRotateKnobImg.getHeight() * KnotRatio * scale)) / 2;

        matrix1.postTranslate(centreX1, centreY1);
        matrix1.postRotate(mRotatingDeg, canvasWidth/2, canvasHeight/2);
        //matrix1.postSkew(0.5f, 0.5f);

        canvas.drawBitmap(mKnobScaleImg, matrix, null);
        canvas.drawBitmap(mRotateKnobImg, matrix1, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        int action = ev.getAction() & MotionEvent.ACTION_MASK;
        int pointCnt = ev.getPointerCount();

        if(pointCnt >= GESTURE_POINTER_COUNTS){// || pointCnt == GESTURE_POINTER_COUNTS + 1) {

            if(mHandler.hasMessages(HIDE_KNOB_MSG)) {
                mHandler.removeMessages(HIDE_KNOB_MSG);
            }

            mVisibleKnob = true;

            if(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN){

                invalidate();
                for (int i = 0; i < pointCnt; i++) {

                    int id = ev.getPointerId(i);
                    mPrevTouchPt.put(id, new Point(ev.getX(i), ev.getY(i)));
                }
            }
        }
        else{
            if(!mHandler.hasMessages(HIDE_KNOB_MSG) && mVisibleKnob) {
                Log.d(TAG, "Send HIDE_KNOB_MSG");
                mHandler.sendMessageDelayed(mHandler.obtainMessage(HIDE_KNOB_MSG), 500);
            }
        }

        if( mVisibleKnob ){
            detectFingersRotation(ev);
        }

        return true;
    }

    private static final int HIDE_KNOB_MSG = 1000;

    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case HIDE_KNOB_MSG:

                    mVisibleKnob = false;
                    mPrevTouchPt.clear();
                    mMovingTouchPt.clear();
                    mPrevFilterClockwise = 0f;
                    mPrevFilterRotateDist = 0f;

                    invalidate();
                    break;
            }
        }
    };

    protected void detectFingersRotation(MotionEvent ev){
        int pointCnt = ev.getPointerCount();

        for (int i = 0; i < pointCnt; i++) {
            int id = ev.getPointerId(i);

            if(!mPrevTouchPt.containsKey(id)) continue;

            float dx = ev.getX(i) - mPrevTouchPt.get(id).x;
            float dy = ev.getY(i) - mPrevTouchPt.get(id).y;

            if(Math.abs(dx) > 1.0 && Math.abs(dy) > 1.0){

                mMovingTouchPt.put(id, new Point(ev.getX(i), ev.getY(i)));
            }
        }

        if(mMovingTouchPt.size() >= GESTURE_POINTER_COUNTS ){

            if(checkRotationInfo()){
                invalidate();

                for(int id : mMovingTouchPt.keySet()){
                    mPrevTouchPt.put(id, mMovingTouchPt.get(id));
                }

                mMovingTouchPt.clear();
            }
        }
    }

    int leftId, rightId, topId, bottomId;

    boolean checkRotationInfo() {

        Point leftPt = new Point(Float.MAX_VALUE, Float.MAX_VALUE);
        Point rightPt = new Point(-Float.MAX_VALUE, -Float.MAX_VALUE);
        Point topPt = new Point(Float.MAX_VALUE, Float.MAX_VALUE);
        Point bottomPt = new Point(-Float.MAX_VALUE, -Float.MAX_VALUE);

        for (int id : mPrevTouchPt.keySet()) {
            Point prePt = mPrevTouchPt.get(id);

            if (mMovingTouchPt.containsKey(id)) {
                Point curPt = mMovingTouchPt.get(id);

                if (prePt.x < leftPt.x) {
                    leftPt = prePt;
                    leftId = id;
                }

                if (prePt.x > rightPt.x) {
                    rightPt = prePt;
                    rightId = id;
                }

                if (prePt.y < topPt.y) {
                    topPt = prePt;
                    topId = id;
                }

                if (prePt.y > bottomPt.y) {
                    bottomPt = prePt;
                    bottomId = id;
                }
            }
        }

        int clockwise = 0;
        Point prePt = mPrevTouchPt.get(leftId);
        Point curPt = mMovingTouchPt.get(leftId);

        float moveDist = 0;
        if(curPt.y - prePt.y < 0){
            clockwise++;
            moveDist += prePt.y - curPt.y;
        }
        else{
            clockwise--;
            moveDist += curPt.y - prePt.y;
        }

        prePt = mPrevTouchPt.get(rightId);
        curPt = mMovingTouchPt.get(rightId);
        if(curPt.y - prePt.y > 0){
            clockwise++;
            moveDist += curPt.y - prePt.y;
        }
        else{
            clockwise--;
            moveDist += prePt.y - curPt.y;
        }

        prePt = mPrevTouchPt.get(topId);
        curPt = mMovingTouchPt.get(topId);
        if(curPt.x - prePt.x > 0){
            clockwise++;
            moveDist += curPt.x - prePt.x;
        }
        else{
            clockwise--;
            moveDist += prePt.x - curPt.x;
        }

        prePt = mPrevTouchPt.get(bottomId);
        curPt = mMovingTouchPt.get(bottomId);
        if(curPt.x - prePt.x < 0){
            clockwise++;
            moveDist += prePt.x - curPt.x;
        }
        else{
            clockwise--;
            moveDist += curPt.x - prePt.x;
        }

        float fclockwise = filterClockwise(clockwise);
        moveDist = filterRotateDist(moveDist, clockwise);

        Log.d(TAG, "fclockwise="+fclockwise+",dist="+moveDist);

        if(fclockwise >= 2.0 ){

            mRotatingDeg += moveDist * 0.07f;
            return true;
        }
        else if(fclockwise <= -2.0 ){
            mRotatingDeg -= moveDist * 0.07f;
            return true;
        }

        return false;
    }


    private float mPrevFilterClockwise;
    private float filterClockwise(int c){
        mPrevFilterClockwise = 0.4f * c + 0.6f * mPrevFilterClockwise;
        return mPrevFilterClockwise;
    }

    private float mPrevFilterRotateDist;
    private float filterRotateDist(float d, float c){
        mPrevFilterRotateDist = 0.6f * mPrevFilterRotateDist + 0.4f * d;
        return mPrevFilterRotateDist;
    }

    static private class Point{
        public float x, y;

        public Point(float x, float y){
            this.x = x;
            this.y = y;
        }
    }
}
