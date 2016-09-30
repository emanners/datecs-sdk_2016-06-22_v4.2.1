package com.datecs.hubdemo;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class SlaveAdapter extends RecyclerView.Adapter<SlaveAdapter.ViewHolder> {

    private final List<SlaveItem> mValues;

    private final OnItemClickListener mListener;

    public SlaveAdapter(OnItemClickListener listener) {
        mValues = new ArrayList<>();
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.slave_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        SlaveItem item = mValues.get(position);
        holder.mItem = mValues.get(position);
        holder.mIconView.setImageResource(item.getIcon());
        holder.mNameView.setText(item.getName());
        holder.mDescriptionView.setText(item.getDescription());

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onItemClick(mValues.get(position));
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public void put(SlaveItem item) {
        int index = mValues.indexOf(item);
        if (index < 0) {
            mValues.add(item);
            notifyItemInserted(mValues.size() - 1);
        } else {
            mValues.set(index, item);
            notifyDataSetChanged();
        }
    }

    public void clear() {
        mValues.clear();
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ImageView mIconView;
        public final TextView mNameView;
        public final TextView mDescriptionView;
        public SlaveItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIconView = (ImageView) view.findViewById(R.id.icon);
            mNameView = (TextView) view.findViewById(R.id.name);
            mDescriptionView = (TextView) view.findViewById(R.id.description);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SlaveItem item);
    }
}
