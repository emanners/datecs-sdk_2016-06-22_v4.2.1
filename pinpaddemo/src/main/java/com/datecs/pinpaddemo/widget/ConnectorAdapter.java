package com.datecs.pinpaddemo.widget;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.datecs.pinpaddemo.R;
import com.datecs.pinpaddemo.connectivity.AbstractConnector;
import com.datecs.pinpaddemo.connectivity.BluetoothConnector;
import com.datecs.pinpaddemo.connectivity.NetworkConnector;
import com.datecs.pinpaddemo.connectivity.UsbDeviceConnector;

import java.util.List;

public class ConnectorAdapter extends RecyclerView.Adapter<ConnectorAdapter.ViewHolderItem> {

    public interface OnItemClickListener {
        void onItemClick(View view, AbstractConnector item);
    }

    private List<AbstractConnector> mItems;
    private OnItemClickListener mListener;

    public ConnectorAdapter(List<AbstractConnector> items, ConnectorAdapter.OnItemClickListener l) {
        this.mItems = items;
        this.mListener = l;
    }

    @Override public ViewHolderItem onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.connector_list_item, parent, false);
        return new ViewHolderItem(view);
    }

    @Override public void onBindViewHolder(ConnectorAdapter.ViewHolderItem holder, final int position) {
        final AbstractConnector item = getItem(position);
        if (item instanceof NetworkConnector) {
            NetworkConnector connector = (NetworkConnector) item;
            holder.icon.setImageResource(R.drawable.ic_network);
            holder.name.setText("HOST: " + connector.getHost());
            holder.desc.setText("PORT: " + connector.getPort());
        } else if (item instanceof UsbDeviceConnector) {
            UsbDeviceConnector connector = (UsbDeviceConnector) item;
            holder.icon.setImageResource(R.drawable.ic_usb);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.name.setText(connector.getDevice().getProductName());
            } else {
                holder.name.setText("USB DEVICE");
            }
            holder.desc.setText(connector.getDevice().getDeviceName());
        } else if (item instanceof BluetoothConnector) {
            BluetoothConnector connector = (BluetoothConnector) item;
            if (connector.getBluetoothDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                holder.icon.setImageResource(R.drawable.ic_bluetooth_paired);
            } else {
                holder.icon.setImageResource(R.drawable.ic_bluetooth);
            }
            String name = connector.getBluetoothDevice().getName();
            if (name == null || name.isEmpty()) {
                holder.name.setText("BLUETOOTH DEVICE");
            } else {
                holder.name.setText(connector.getBluetoothDevice().getName());
            }
            holder.desc.setText(connector.getBluetoothDevice().getAddress());
        } else {
            throw new IllegalArgumentException("Invalid connector");
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("OnClick");
                mListener.onItemClick(v, item);
            }
        });
    }

    @Override public int getItemCount() {
        return mItems.size();
    }

    private AbstractConnector getItem(int position) {
        return mItems.get(position);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void add(AbstractConnector connector) {
        add(mItems.size(), connector);
    }

    public void add(int position, AbstractConnector connector) {
        mItems.add(position, connector);
        notifyItemInserted(position);
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public static class ViewHolderItem extends RecyclerView.ViewHolder {
        public ImageView icon;
        public TextView name;
        public TextView desc;

        public ViewHolderItem(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            name = (TextView) itemView.findViewById(R.id.name);
            desc = (TextView) itemView.findViewById(R.id.description);
        }
    }
}