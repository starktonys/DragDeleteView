package me.stark.tony.dragdeleteview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView mIvLauncher = findViewById(R.id.iv_launcher);

        // 与点击事件无冲突
        mIvLauncher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "perform OnClick", Toast.LENGTH_SHORT).show();
            }
        });
        DragDeleteView.attach(mIvLauncher, new DragDeleteView.Callback() {
            @Override
            public void onDelete() {
                Toast.makeText(MainActivity.this, "被拖拽删除了", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
