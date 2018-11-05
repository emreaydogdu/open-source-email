package tr.eaydgdu.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018 by Marcel Bokhorst (M66B)
*/

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import tr.eaydgdu.email.database.DB;

public class ReceiverAutostart extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int synchronizing = DB.getInstance(context).account().getSynchronizingAccountCount();
                    Log.i(Helper.TAG, "Synchronizing accounts=" + synchronizing);
                    if (synchronizing > 0)
                        ServiceSynchronize.init(context);
                    JobDaily.schedule(context);
                }
            }).start();
    }
}
