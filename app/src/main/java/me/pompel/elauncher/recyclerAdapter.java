package me.pompel.elauncher;

import android.annotation.SuppressLint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;


public class recyclerAdapter extends RecyclerView.Adapter<recyclerAdapter.AppViewHolder> implements Filterable {
    private final ArrayList<App> appList;
    private ArrayList<App> appListFiltered;
    private final RecyclerViewClickListener listener;
    private String activeQuery = "";
    private boolean filteringEnabled = true;

    public recyclerAdapter(ArrayList<App> appList, RecyclerViewClickListener listener) {
        this.appList = appList;
        this.appListFiltered = new ArrayList<>();
        this.listener = listener;
    }

    public void filter(CharSequence query) {
        filteringEnabled = true;
        activeQuery = query.toString();
        getFilter().filter(query);
    }

    public void pauseFiltering() {
        filteringEnabled = false;
    }

    private static boolean fuzzyContains(String str, String query) {
        int strIndex = 0;
        for (char c : query.toCharArray()) {
            strIndex = str.indexOf(c, strIndex);
            if (strIndex == -1) return false;
        }
        return true;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String str = charSequence.toString().toLowerCase();
                FilterResults results = new FilterResults();
                List<App> filteredApps = new ArrayList<>();

                if (!str.isEmpty()) {
                    for (App app : appList) {
                        if (fuzzyContains(app.appName.toString().toLowerCase(), str)) filteredApps.add(app);
                    }
                }

                results.count = filteredApps.size();
                results.values = filteredApps;
                return results;
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                if (!filteringEnabled || !activeQuery.equals(charSequence.toString())) return;
                appListFiltered = (ArrayList<App>)filterResults.values;

                if (appListFiltered.isEmpty()) {
                    listener.onNoMatch(charSequence.toString());
                }

                for (App app : appListFiltered) {
                    String appName = app.appName.toString().toLowerCase();
                    String query = charSequence.toString().toLowerCase();
                    int queryIndex = 0;
                    for (int appNameIndex = 0;
                            appNameIndex < appName.length() && queryIndex < query.length();
                            appNameIndex++) {
                        if (appName.charAt(appNameIndex) == query.charAt(queryIndex)) {
                            app.appName.setSpan(new UnderlineSpan(), appNameIndex, appNameIndex + 1,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            queryIndex++;
                        }
                    }

                    // if an exact match, exit and click on it
                    if (app.appName.length() == charSequence.length() && queryIndex == query.length()) {
                        listener.onClick(app);
                        break;
                    }
                }

                if (appListFiltered.size() == 1) listener.onClick(appListFiltered.get(0));
                notifyDataSetChanged();
            }
        };
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView nameText;

        @Override
        public void onClick(View view) { listener.onClick(appListFiltered.get(getAbsoluteAdapterPosition())); }

        @Override
        public boolean onLongClick(View view) {
            listener.onLongClick(appListFiltered.get(getAbsoluteAdapterPosition()));
            return true;
        }

        public AppViewHolder(final View view) {
            super(view);
            nameText = view.findViewById(R.id.app_name);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }
    }

    @NonNull
    @Override
    public recyclerAdapter.AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull recyclerAdapter.AppViewHolder holder, int position) {
        SpannableString appName = appListFiltered.get(position).appName;
        holder.nameText.setText(appName);

        // remove all the spans after the string has been set
        Object[] spans = appName.getSpans(0, appName.length(), Object.class);
        for (Object span : spans) {
            appName.removeSpan(span);
        }
    }

    @Override
    public int getItemCount() {
        return appListFiltered.size();
    }

    public interface RecyclerViewClickListener {
        void onClick(App app);
        void onLongClick(App app);

        default void onNoMatch(String query) {
        }
    }
}
