package com.datecs.hubdemo;

import com.datecs.hub.Hub;

/**
 * Helper class for providing sample content for slave devices.
 */
public class SlaveItem {
    public final int id;
    public final int type;
    public final int state;
    public final String descriptor;
    public final String manufacturer;
    public final String model;

    public SlaveItem(int id, int type, int state, String descriptor) {
        this.id = id;
        this.type = type;
        this.state = state;
        this.descriptor = descriptor;

        // Additional information
        if (this.descriptor != null) {
            String[] items = descriptor.split("\n");
            this.manufacturer = items[0];
            this.model = items[1];
        } else {
            this.manufacturer = null;
            this.model = null;
        }
    }

    public int getIcon() {
        if (type == Hub.SLAVE_TYPE_USB) {
            if (isPrinterConnected()) {
                return R.drawable.ic_printer;
            } else {
                return R.drawable.ic_usb;
            }
        } else {
            return R.drawable.ic_rs232;
        }
    }

    public String getName() {
        if (type == Hub.SLAVE_TYPE_USB) {
            return "USB port";
        } else {
            return "RS232 port";
        }
    }

    public String getDescription() {
        if (state == Hub.SLAVE_STATE_READY) {
            if (model != null) {
                return model;
            } else {
                return "<Unknown device>";
            }
        } else {
            return "Port is not ready";
        }
    }

    public boolean isPrinterConnected() {
        if (manufacturer != null && manufacturer.equals("DATECS")) {
            if (model != null && model.startsWith("Portable Printer DPP")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof SlaveItem) {
            return id == (((SlaveItem)o).id);
        }
        return false;
    }
}