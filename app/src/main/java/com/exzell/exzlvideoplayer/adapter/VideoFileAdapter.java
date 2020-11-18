package com.exzell.exzlvideoplayer.adapter;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.Request;
import com.exzell.exzlvideoplayer.MediaFile;
import com.exzell.exzlvideoplayer.PopupDialogFragment;
import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.utils.BuildUtilsKt;
import com.exzell.exzlvideoplayer.utils.FileUtilsKt;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.google.android.material.internal.ContextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@SuppressLint("InflateParams")
public class VideoFileAdapter extends RecyclerView.Adapter<VideoFileAdapter.ViewHolder> {

    private final String TAG = getClass().getSimpleName();
    private LayoutInflater mLayoutInflater;
    private Context mContext;
    private ExecutorService mExecutor;
    private Handler mHandler;
    private View.OnClickListener mListener;
    private boolean isLinear;
    private SelectionTracker mSelectionTracker;
    private List<MediaFile> mFiles;
    private static DiffUtil.ItemCallback<MediaFile> mCallback = new DiffUtil.ItemCallback<MediaFile>() {
        @Override
        public boolean areItemsTheSame(@NonNull MediaFile oldItem, @NonNull MediaFile newItem) {
            return oldItem.getDisplayName().equals(newItem.getDisplayName());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MediaFile oldItem, @NonNull MediaFile newItem) {
            return oldItem.equals(newItem);
        }
    };

    public VideoFileAdapter(List<MediaFile> infos, Context context, boolean isLinear) {

        mFiles = new ArrayList<>(infos);
        mLayoutInflater = LayoutInflater.from(context);
        mContext = context;
        mExecutor = Executors.newSingleThreadExecutor();
        mHandler = new Handler(mContext.getMainLooper());
        this.isLinear = isLinear;

        setHasStableIds(true);
    }

    public void setTracker(SelectionTracker track) {
        mSelectionTracker = track;
    }

    public void setListener(View.OnClickListener lisener){
        mListener = lisener;
    }

    public void setManager(boolean isLinear) {
        this.isLinear = isLinear;
        notifyItemRangeChanged(0, mFiles.size());
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

        Log.i(TAG, "Position called in Bind View Holder: " + position);
        MediaFile file = mFiles.get(position);

        holder.itemView.setOnClickListener(mListener);
        holder.mTextName.setText(file.getDisplayName());
        String mb = FileUtilsKt.sizeInMb(file.getSize(), mExecutor);
        holder.mTextSize.setText(mb);


        if(file.getType().equals(MediaFile.Type.DIRECTORY)) holder.mDefaultDrawable = R.drawable.ic_folder_black_24dp;
        else if(file.getType().equals(MediaFile.Type.AUDIO)) holder.mDefaultDrawable = R.drawable.ic_music_note_black_24dp;
        else holder.mDefaultDrawable = R.drawable.ic_folder_black_24dp;


        if (file.getType().equals(MediaFile.Type.DIRECTORY)) {
            holder.mTextDuration.setVisibility(View.GONE);

        } else {
            holder.mTextDuration.setVisibility(View.VISIBLE);

            if(file.getDuration() > 0) holder.mTextDuration.setText(timeFormat(MediaUtils.microUsToTime(file.getDuration())));
            else holder.setMediaDuration(file);
        }

        if(file.getType() != MediaFile.Type.DIRECTORY && file.getThumbnailPath() == null) holder.saveThumbnail(file);
        else loadResource(file.getThumbnailPath(), holder);

        if (mSelectionTracker != null)
            holder.itemView.setActivated(mSelectionTracker.isSelected(getItemId(position)));
    }

    private<T> void loadResource(T type, ViewHolder vh){

        Request request = Glide.with(mContext)
                .load(type)
                .error(vh.mDefaultDrawable)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(vh.mImageView)
                .getRequest();

        request.clear();

        if(!request.isRunning()) request.begin();
    }

    public void setNewFiles(List<MediaFile> newFiles){
        int size = mFiles.size();
        mFiles.clear();
        notifyItemRangeRemoved(0, size);

        mFiles.addAll(newFiles);
        notifyItemRangeInserted(0, newFiles.size());
    }

    public List<MediaFile> getFiles(){return mFiles;}

    @Override
    public long getItemId(int position) {
        return position;
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

    public String getFileTitle(){
        String parentPath = new File(mFiles.get(0).getPath()).getParent();
        return new File(parentPath).getName();
    }

    public void removeFile(MediaFile file){

        int index = mFiles.indexOf(file);
        if(index != -1){
            mFiles.remove(index);
            notifyItemRemoved(index);
        }

    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView mImageView;
        public TextView mTextName;
        TextView mTextSize;
        TextView mTextDuration;
        private int mDefaultDrawable;
        public ItemDetailsLookup.ItemDetails<Long> mDetails;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.image_thumbnail);
            ImageView mImageMore = itemView.findViewById(R.id.image_more);
            mTextName = itemView.findViewById(R.id.text_file_name);
            mTextSize = itemView.findViewById(R.id.text_size);
            mTextDuration = itemView.findViewById(R.id.text_duration);
            mTextName.setId(View.generateViewId());

            mImageMore.setOnClickListener(v -> showPopup(v));

            setDetails();
        }

        public void setDetails() {
            mDetails = new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return getBindingAdapterPosition();
                }

                @Nullable
                @Override
                public Long getSelectionKey() {
                    return getItemId();
                }
            };
        }

        void saveThumbnail(final MediaFile file) {

            Observable.just(file)
                    .subscribeOn(Schedulers.io())
                    .map(f -> {

                        File app = BuildUtilsKt.thumbnailPath(mContext);

                        return MediaUtils.loadThumbIntoCache(app, file.getPath(), null);
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(c -> c.printStackTrace())
                    .doOnNext(c -> {

                        file.setThumbnailPath(c);
                        loadResource(c, this);
            }).subscribe();
        }

        void setMediaDuration(MediaFile file) {
            Observable.just(file)
                    .subscribeOn(Schedulers.computation())
                    .map(f -> {
                        long duration = f.getDuration();
                        file.setDuration(duration);

                        return timeFormat(MediaUtils.microUsToTime(f.getDuration()));
                    }).observeOn(AndroidSchedulers.mainThread())
                    .doOnError(c -> c.printStackTrace())
                    .doOnNext(c -> {
                        mTextDuration.setText(c);
                    }).subscribe();
        }

        @SuppressLint("RestrictedApi")
        void showPopup(final View view) {

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

        private void performPopupAction(int itemId) {

            int action = -1;
            switch (itemId) {
                case R.id.action_change_thumb:
                    action = PopupDialogFragment.ACTION_THUMBNAIL;
                    break;
                case R.id.action_delete:
                    action = PopupDialogFragment.ACTION_DELETE;
                    break;
                case R.id.action_rename:
                    action = PopupDialogFragment.ACTION_RENAME;
                    break;
            }

            MediaFile f = mFiles.get(getAbsoluteAdapterPosition());
            PopupDialogFragment popup = PopupDialogFragment.getInstance(action, f);
            popup.setThumbnailListener(() -> notifyItemChanged(getBindingAdapterPosition()));

            if(mContext instanceof FragmentActivity){
                popup.show(((FragmentActivity) mContext).getSupportFragmentManager(), null);
            }else Toast.makeText(mContext, "No manager to give dialog", Toast.LENGTH_SHORT).show();
        }
    }
}
