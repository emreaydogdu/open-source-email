package tr.eaydgdu.email.views;

import android.app.Dialog;
import android.os.Bundle;

import tr.eaydgdu.email.R;

public class BottomSheetDialog extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new com.google.android.material.bottomsheet.BottomSheetDialog(getContext(),getTheme());
    }

}
