package com.exzell.exzlvideoplayer.adapter;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.exzell.exzlvideoplayer.MediaFile;
import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.utils.FileUtils;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shawnlin.numberpicker.NumberPicker;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("InflateParams")
public class VideoFileAdapter extends RecyclerView.Adapter<VideoFileAdapter.ViewHolder> {

    private final String TAG = getClass().getSimpleName();
    private LayoutInflater mLayoutInflater;
    private Context mContext;
    private List<MediaFile> mFiles;
    private ExecutorService mExecutor;
    private Handler mHandler;
    private boolean isLinear;
    private SelectionTracker mSelectionTracker;

    public VideoFileAdapter(List<MediaFile> infos, Context context, boolean isLinear) {

        mLayoutInflater = LayoutInflater.from(context);
        mFiles = infos;
        mContext = context;
        mExecutor = Executors.newCachedThreadPool();
        mHandler = new Handler(mContext.getMainLooper());
        this.isLinear = isLinear;
    }

    public VideoFileAdapter(Context context, List<MediaFile> withPath, boolean isLinear) {
        this(null, context, isLinear);
        mFiles = withPath;
    }

    public void setNewFiles(List<MediaFile> newFiles) {
        mFiles = newFiles;
        notifyDataSetChanged();
    }

    public void passTracker(SelectionTracker track) {
        mSelectionTracker = track;
    }

    public void setManager(boolean isLinear) {
        this.isLinear = isLinear;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (isLinear)
            return new ViewHolder(mLayoutInflater.inflate(R.layout.list_view_items, parent, false));
        else
            return new ViewHolder(mLayoutInflater.inflate(R.layout.list_view_items_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {

        MediaFile file = mFiles.get(position);

        holder.mTextName.setText(file.getDisplayName());
        holder.filePath = file.getPath();
        holder.mImageView.setImageDrawable(null);
        String mb = FileUtils.sizeInMb(file.getSize());
        holder.mTextSize.setText(mb);

        if (!file.isDir()) {
            holder.mTextDuration.setVisibility(View.VISIBLE);

            if(file.getDuration() > 0) holder.mTextDuration.setText(timeFormat(MediaUtils.microUsToTime(file.getDuration())));
            else holder.getMediaDuration(file);

            if (file.getThumbnail() != null) holder.mImageView.setImageBitmap(file.getThumbnail());
            else holder.getThumbNail(file);

        } else {
            holder.mTextDuration.setVisibility(View.GONE);
            Drawable folder = mContext.getResources().getDrawable(R.drawable.ic_folder_black_24dp, null);
            holder.mImageView.setImageDrawable(folder);
        }

        if (mSelectionTracker != null)
            holder.itemView.setActivated(mSelectionTracker.isSelected(getItemId(position)));
    }

    @Override
    public long getItemId(int position) {
        long id = 0;
        MediaFile mediaFile = mFiles.get(position);
        for (int i = 0; i < mediaFile.getPath().length(); i++) {
            id += mediaFile.getPath().charAt(i);
        }
        return id;
    }

    private String timeFormat(final int[] mcs) {
        String ret = "";

        try {
            ret =
                    mExecutor.submit(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            StringBuilder time = new StringBuilder();
                            int len = mcs.length;
                            int i = 0;
                            if (mcs[0] == 0) i = 1;

                            while (i < len) {
                                String ti = Integer.toString(mcs[i]);
                                if (ti.length() == 1) ti = "0" + ti;
                                time.append(ti);
                                time.append(":");
                                i++;
                            }

                            if (time.toString().endsWith(":")) time.deleteCharAt(time.length() - 1);

                            return time.toString();
                        }
                    }).get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    @Override
    public int getItemViewType(int position) {
        if (isLinear) return 0;
        else return 1;
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    public List<MediaFile> getFiles() {
        return mFiles;
    }

    public String getFileTitle(){
        String parentPath = new File(mFiles.get(0).getPath()).getParent();
        return new File(parentPath).getName();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView mImageView;
        public TextView mTextName;
        TextView mTextSize;
        TextView mTextDuration;
        public String filePath;
        private Drawable mAudioNote;
        private Drawable mMovie;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.image_thumbnail);
            ImageView mImageMore = itemView.findViewById(R.id.image_more);
            mTextName = itemView.findViewById(R.id.text_file_name);
            mTextSize = itemView.findViewById(R.id.text_size);
            mTextDuration = itemView.findViewById(R.id.text_duration);
            mTextName.setId(View.generateViewId());
            mAudioNote = mContext.getResources().getDrawable(R.drawable.ic_music_note_black_24dp, null);
            mMovie = mContext.getResources().getDrawable(R.drawable.ic_movie_black_24dp, null);


//            mImageView.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    selectFile();
//                }
//            });

//            itemView.setOnLongClickListener(new View.OnLongClickListener() {
//                @Override
//                public boolean onLongClick(View v) {
//                    return selectFile();
//                }
//            });

            mImageMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPopup(v);
                }
            });

            setDetails();
        }

        public ItemDetailsLookup.ItemDetails<Long> setDetails() {
            return new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return getAdapterPosition();
                }

                @Nullable
                @Override
                public Long getSelectionKey() {
                    return getItemId();
                }
            };
        }

        void getThumbNail(final MediaFile file) {
            new Thread(new Runnable() {
                @Override
                public void run() {

                    final MediaUtils.MediaOrigins which = MediaUtils.checkFileType(new File(filePath));

                    int wid = (int) mContext.getResources().getDimension(R.dimen.image_width);
                    int hei = (int) mContext.getResources().getDimension(R.dimen.image_height);


                    File app = mContext.getApplicationContext().getExternalCacheDir();

                    MediaUtils.loadThumbIntoCache(app, ViewHolder.this.filePath, null);

                    final Bitmap fileThumb = MediaUtils.getFileThumbnail(app, filePath, wid, hei);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (fileThumb != null) {
                                mImageView.setImageBitmap(fileThumb);
                                file.setThumbnail(fileThumb);

                            } else {
                                if (which == MediaUtils.MediaOrigins.AUDIO)
                                    mImageView.setImageDrawable(mAudioNote);
                                else mImageView.setImageDrawable(mMovie);
                            }
                        }
                    });
                }
            }).start();
        }

        void getMediaDuration(final MediaFile file) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    long duration = Long.parseLong(MediaUtils.getMediaDuration(filePath)) * 1000;
                    file.setDuration(duration);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mTextDuration.setText(timeFormat(MediaUtils
                                    .microUsToTime(file.getDuration())));
                        }
                    });
                }
            }).start();
        }

        @SuppressLint("RestrictedApi")
        void showPopup(final View view) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    MenuBuilder menuBuilder = new MenuBuilder(mContext);
                    new MenuInflater(mContext).inflate(R.menu.menu_popup_more, menuBuilder);
                    MenuPopupHelper popHelp = new MenuPopupHelper(mContext, menuBuilder, view);
                    popHelp.setForceShowIcon(true);

                    menuBuilder.setCallback(new MenuBuilder.Callback() {
                        @Override
                        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                            performPopupAction(item.getItemId());
                            Log.i(TAG, "Menu item clicked");
                            return true;
                        }

                        @Override
                        public void onMenuModeChange(MenuBuilder menu) {
                        }
                    });

                    popHelp.show();
                }
            });
        }

        private void performPopupAction(int itemId) {
            switch (itemId) {
                case R.id.action_change_thumb:
                    popUpDialog(0);
                    break;
                case R.id.action_delete:
                    popUpDialog(1);
                    break;
                case R.id.action_rename:
                    popUpDialog(2);
                    break;
            }
        }

        private void popUpDialog(final int kind) {

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String title = null;
                    if (kind == 0) title = "Enter desired time";
                    else if (kind == 2) title = "New Name";

                    final AlertDialog simpDia = new MaterialAlertDialogBuilder(mContext)
                            .setTitle(title)
                            .setNegativeButton("Cancel", createDialogListener(kind, null))
                            .create();

                    //A simple edit text to change the name
                    if (kind == 2) simpDia.setView(dialogEditText(simpDia));
                        //Three pickers showing the respective time unit
                    else if (kind == 0) {
                        final LinearLayout picker = createDialogPicker();
                        simpDia.setView(picker);
                        simpDia.setButton(AlertDialog.BUTTON_POSITIVE, "Change Thumbnail", createDialogListener(kind, picker));
                    } else {
                        simpDia.setMessage("Are you sure you want to delete this File \nThis operation cannot be undone");
                        simpDia.setButton(AlertDialog.BUTTON_POSITIVE, "Delete", createDialogListener(kind, null));
                    }

                    simpDia.show();

                    changeDialogForm(simpDia);
                }
            });

        }

        private EditText dialogEditText(final AlertDialog parent) {

            final EditText simpEdit = (EditText) mLayoutInflater.inflate(R.layout.switch_item, null, false);
            simpEdit.setText(mTextName.getText());

            simpEdit.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    simpEdit.requestFocus();
                    int extensionStartIndex = mTextName.getText().toString().lastIndexOf(".");
                    simpEdit.setSelection(0, extensionStartIndex);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    simpEdit.removeOnAttachStateChangeListener(this);
                }
            });


            simpEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId != EditorInfo.IME_NULL) return false;

                    parent.cancel();

                    boolean done = MediaUtils.renameFile(mContext, filePath, v.getText().toString());

                    if (done) Toast.makeText(mContext, "Renamed Successfully", Toast.LENGTH_SHORT).show();
                    else Toast.makeText(mContext, "Rename Failed", Toast.LENGTH_SHORT).show();

                    return true;
                }
            });

            return simpEdit;
        }

        private DialogInterface.OnClickListener createDialogListener(final int kind, final View v) {
            return new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == Dialog.BUTTON_POSITIVE) {
                        if (kind == 1) {
                            boolean done = MediaUtils.deleteFile(mContext, filePath);
                            if (done)
                                Toast.makeText(mContext, "Deleted Successfully", Toast.LENGTH_SHORT).show();
                        } else v.performClick();
                    } else dialog.cancel();
                }
            };
        }

        private void changeDialogForm(AlertDialog dia) {
            View diaDecor = dia.getWindow().peekDecorView();
            dia.getWindow().setGravity(Gravity.CENTER);

            WindowManager.LayoutParams diaParams = (WindowManager.LayoutParams) diaDecor.getLayoutParams();
            if (diaParams != null) {
                diaParams.width = diaParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                dia.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }

        private LinearLayout createDialogPicker() {
            final LinearLayout parent = (LinearLayout) mLayoutInflater.inflate(R.layout.thumbnail_pickers, null, false);

            long timeUs = MediaUtils.timeInUs(mTextDuration.getText().toString());
            int[] times = MediaUtils.microUsToTime(timeUs);

            //indexes 0, 2, 4 are the pickers
            for (int i = 0; i < times.length; i++) {

                NumberPicker pick;
                if (i == 0) pick = (NumberPicker) parent.getChildAt(0);
                else if (i == 1) pick = (NumberPicker) parent.getChildAt(2);
                else pick = (NumberPicker) parent.getChildAt(4);

//                pick.setFormatter(new NumberPicker.Formatter() {
//                    @Override
//                    public String format(int value) {
//                        if (value < 10) return "0" + value;
//                        else return String.valueOf(value);
//                    }
//                });
                pick.setOnLongPressUpdateInterval(2);

                if (times[0] != 0 && i == 0) pick.setMaxValue(times[i]);
                else if (times[1] != 0 && i == 1) pick.setMaxValue(times[i]);
            }

            if (times[0] == 0) {
                parent.removeViewAt(1);
                parent.removeViewAt(0);
            }

            parent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mImageView.setImageDrawable(null);
                    int childCount = parent.getChildCount();
                    StringBuilder time = new StringBuilder();
                    for (int i = 0; i < childCount; i++) {
                        View c = parent.getChildAt(i);
                        if (c instanceof NumberPicker) time.append(((NumberPicker) c).getValue());
                        else time.append(((TextView) c).getText().toString());
                    }
                    MediaUtils.loadThumbIntoCache(mContext.getExternalCacheDir(), filePath, time.toString());

                    int myPos = getAdapterPosition();
                    getThumbNail(mFiles.get(myPos));
                }
            });

            return parent;
        }
    }
}
