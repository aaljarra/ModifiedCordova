package com.example.cciloaner.testpunch;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Point;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity {

    int c=0;
    int minx = 0, min_y= 84, maxx=1425, maxy = 1810 ;
    ArrayList<Point> coord = new ArrayList<>();
    ArrayList<Long> times = new ArrayList<Long>();
    ExecutorService threadpool;

    void computeCoors () {
        int height  = maxy - min_y ;
        //   Log.d("abeer", "height = "+height) ;
        int width = maxx - minx ;
        //  Log.d("abeer", "width = "+width) ;
        int n=10;
        int jump_x = width/n;
        int jump_y = height/n ;
        // Log.d("abeer", "jump x = "+jump_x) ;
        //  Log.d("abeer", "jump y = "+jump_y) ;

        for(int y= min_y, i=0 ; y<= maxy ; y+= jump_y, i++){

            int midy = ((jump_y*(i+1) + min_y) - y)/2 + y;

            for( int j=0 ,  x=minx ; x<= maxx ; x+= jump_x, j++){
                int midx = ((jump_x*(j+1) + minx) - x)/2 + x;
                //Log.d("abeer", "for [" + i + "][" + j + "] values are x : " + midx + " , y=" + midy) ;
                coord.add(new Point(midx, midy)) ;
            }
        }
        //Log.d("abeer", "coord size "+coord.size());
        return ;
    }

    void toastMessage(String msg){
        Toast.makeText(getApplication(),msg,Toast.LENGTH_LONG).show();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
       // this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);


        Button b = (Button) findViewById(R.id.ev);

        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                //Toast.makeText(SecureActivity.this, "Clicked @" + event.getRawX() + ", " + event.getRawY(), Toast.LENGTH_SHORT).show();
                //  Log.d("C", "Clicked @" + event.getRawX() + ", " + event.getRawY());
                //Log.d("T", System.currentTimeMillis() + "");
                times.add(new Long(System.currentTimeMillis()));
                // Log.d("T", "After times add : " + times.toString() + "");
                c++;
                // if(c==5) {
                     Collections.sort(times);
                     // Log.d("T", "in getInterval" + times.toString()) ;
                     Log.d("abeer", "times count " + times.size());
                Log.d("abeer", "coords " + event.getRawX() + ", "+ event.getRawY());
                     Log.d("abeer", "time difference " + (times.get(times.size() - 1) + " , " + times.get(0)) + " diff is " + (times.get(times.size() - 1) - times.get(0)));
                // }
                return true;
            }
        });

        class DoClick implements Runnable {
            int x_pos, y_pos;

            public DoClick(int x_pos, int y_pos) {
                this.x_pos = x_pos;
                this.y_pos = y_pos;
            }

            @Override
            public void run() {
                Instrumentation m_Instrumentation = new Instrumentation();
                m_Instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_B);
                m_Instrumentation.sendPointerSync(MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN, x_pos, y_pos, 0));

//                m_Instrumentation.sendPointerSync(MotionEvent.obtain(
//                        SystemClock.uptimeMillis(),
//                        SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
//                        x_pos, y_pos, 0));

            }
        }

        threadpool = Executors.newFixedThreadPool(100);
        computeCoors() ;
        //threadpool.execute(new DoClick(1425, 1810));
        Process p;
        try {
            // Preform su to get root privledges
            p = Runtime.getRuntime().exec("su");

            // Attempt to write a file to a root-only
            //DataOutputStream os = new DataOutputStream(p.getOutputStream());
            //os.writeBytes("echo \"Do I have root?\" >/system/sd/temporary.txt\n");

            // Close the terminal
            //os.writeBytes("exit\n");
           // os.flush();
            try {
                p.waitFor();
                if (p.exitValue() != 255) {
                    // TODO Code to run on success
                    toastMessage("root");
                    Log.d("abeer", "inside if ") ;
                    threadpool.execute(new DoClick(coord.get(0).x, coord.get(0).y));
                    threadpool.execute(new DoClick(coord.get(1).x, coord.get(1).y));
                    threadpool.execute(new DoClick(coord.get(2).x, coord.get(2).y));
                    threadpool.execute(new DoClick(coord.get(3).x, coord.get(3).y));
                    threadpool.execute(new DoClick(coord.get(4).x, coord.get(4).y));
                    threadpool.execute(new DoClick(coord.get(5).x, coord.get(5).y));
                    threadpool.execute(new DoClick(coord.get(6).x, coord.get(6).y));
                    threadpool.execute(new DoClick(coord.get(7).x, coord.get(7).y));
                    threadpool.execute(new DoClick(coord.get(8).x, coord.get(8).y));
                    threadpool.execute(new DoClick(coord.get(9).x, coord.get(9).y));
                    threadpool.execute(new DoClick(coord.get(10).x, coord.get(10).y));
                    threadpool.execute(new DoClick(coord.get(11).x, coord.get(11).y));
                    threadpool.execute(new DoClick(coord.get(12).x, coord.get(12).y));
                    threadpool.execute(new DoClick(coord.get(13).x, coord.get(13).y));
                    threadpool.execute(new DoClick(coord.get(14).x, coord.get(14).y));
                    threadpool.execute(new DoClick(coord.get(15).x, coord.get(15).y));
                    threadpool.execute(new DoClick(coord.get(16).x, coord.get(16).y));
                    threadpool.execute(new DoClick(coord.get(17).x, coord.get(17).y));
                    threadpool.execute(new DoClick(coord.get(18).x, coord.get(18).y));
                    threadpool.execute(new DoClick(coord.get(19).x, coord.get(19).y));
                }
                else {
                    // TODO Code to run on unsuccessful
                    toastMessage("not root");
                    Log.d("abeer", "in else ") ;
                }
            } catch (InterruptedException e) {
                // TODO Code to run in interrupted exception
                toastMessage("not root");
                Log.d("abeer", "interrupt exception  ") ;
            }
        } catch (IOException e) {
            // TODO Code to run in input/output exception
            toastMessage("not root");
            Log.d("abeer", "Exception") ;
        }




    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
