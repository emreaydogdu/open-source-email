package eu.faircode.email;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.util.Log;
import android.widget.TextView;

import org.xml.sax.XMLReader;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.appcompat.app.AppCompatActivity;

public class ActivityMessage extends AppCompatActivity {

    TupleMessageEx message3;
    TextView messageTw;
    private DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        Bundle b = getIntent().getExtras();
        message3 = (TupleMessageEx) (b == null ? -1 : b.getSerializable("message"));


        TextView tw = findViewById(R.id.tw);
        messageTw = findViewById(R.id.message);
        tw.setText(message3.subject);
        messageTw.setText(df.format(new Date(message3.received)));


        if (message3.content) {
            Bundle args = new Bundle();
            args.putSerializable("message", message3);
            bodyTask.load(this, args);
        }

    }


    private SimpleTask<Spanned> bodyTask = new SimpleTask<Spanned>() {
        @Override
        protected void onInit(Bundle args) {
            //btnImages.setHasTransientState(true);
            //tvBody.setHasTransientState(true);
            //pbBody.setHasTransientState(true);
        }

        @Override
        protected Spanned onLoad(final Context context, final Bundle args) throws Throwable {
            TupleMessageEx message = (TupleMessageEx) args.getSerializable("message");
            String body = message.read(context);
            return decodeHtml(message, body);
        }

        @Override
        protected void onLoaded(Bundle args, Spanned body) {
            TupleMessageEx message = (TupleMessageEx) args.getSerializable("message");

            SpannedString ss = new SpannedString(body);
            //boolean has_images = (ss.getSpans(0, ss.length(), ImageSpan.class).length > 0);
            //boolean show_expanded = properties.isExpanded(message.id);
            //boolean show_images = properties.showImages(message.id);

            //btnImages.setVisibility(has_images && show_expanded && !show_images ? View.VISIBLE : View.GONE);
            messageTw.setText(body);
            //pbBody.setVisibility(View.GONE);

            //btnImages.setHasTransientState(false);
            //tvBody.setHasTransientState(false);
            //pbBody.setHasTransientState(false);
        }

        @Override
        protected void onException(Bundle args, Throwable ex) {
            //btnImages.setHasTransientState(false);
            //tvBody.setHasTransientState(false);
            //pbBody.setHasTransientState(false);

            //Helper.unexpectedError(context, ex);
        }
    };


    private Spanned decodeHtml(final EntityMessage message, String body) {
        return Html.fromHtml(HtmlHelper.sanitize(body), new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                float scale = getResources().getDisplayMetrics().density;
                int px = (int) (24 * scale + 0.5f);

                if (source != null && source.startsWith("cid")) {
                    String cid = "<" + source.split(":")[1] + ">";
                    EntityAttachment attachment = DB.getInstance(getApplicationContext()).attachment().getAttachment(message.id, cid);
                    if (attachment == null || !attachment.available) {
                        Drawable d = getResources().getDrawable(R.drawable.baseline_warning_24, getTheme());
                        d.setBounds(0, 0, px, px);
                        return d;
                    } else {
                        File file = EntityAttachment.getFile(getApplicationContext(), attachment.id);
                        Drawable d = Drawable.createFromPath(file.getAbsolutePath());
                        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                        return d;
                    }
                }

                /*
                if (properties.showImages(message.id)) {
                    // Get cache folder
                    File dir = new File(context.getCacheDir(), "images");
                    dir.mkdir();

                    // Cleanup cache
                    long now = new Date().getTime();
                    File[] images = dir.listFiles();
                    if (images != null)
                        for (File image : images)
                            if (image.isFile() && image.lastModified() + CACHE_IMAGE_DURATION < now) {
                                Log.i(Helper.TAG, "Deleting from image cache " + image.getName());
                                image.delete();
                            }

                    InputStream is = null;
                    FileOutputStream os = null;
                    try {
                        if (source == null)
                            throw new IllegalArgumentException("Html.ImageGetter.getDrawable(source == null)");

                        // Create unique file name
                        File file = new File(dir, message.id + "_" + source.hashCode());

                        // Get input stream
                        if (file.exists()) {
                            Log.i(Helper.TAG, "Using cached " + file);
                            is = new FileInputStream(file);
                        } else {
                            Log.i(Helper.TAG, "Downloading " + source);
                            is = new URL(source).openStream();
                        }

                        // Decode image from stream
                        Bitmap bm = BitmapFactory.decodeStream(is);
                        if (bm == null)
                            throw new IllegalArgumentException();

                        // Cache bitmap
                        if (!file.exists()) {
                            os = new FileOutputStream(file);
                            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        }

                        // Create drawable from bitmap
                        Drawable d = new BitmapDrawable(context.getResources(), bm);
                        d.setBounds(0, 0, bm.getWidth(), bm.getHeight());
                        return d;
                    } catch (Throwable ex) {
                        // Show warning icon
                        Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                        Drawable d = context.getResources().getDrawable(R.drawable.baseline_warning_24, context.getTheme());
                        d.setBounds(0, 0, px, px);
                        return d;
                    } finally {
                        // Close streams
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                Log.w(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
                            }
                        }
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                Log.w(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
                            }
                        }
                    }
                } else {*/
                    // Show placeholder icon
                    Drawable d = getResources().getDrawable(R.drawable.baseline_image_24, getTheme());
                    d.setBounds(0, 0, px, px);
                    return d;
                //}
            }
        }, new Html.TagHandler() {
            @Override
            public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
                if (BuildConfig.DEBUG)
                    Log.i(Helper.TAG, "HTML tag=" + tag + " opening=" + opening);
            }
        });
    }


}
