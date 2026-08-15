package me.pompel.elauncher;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Renders command-search feedback; callers decide what an explicit row tap does. */
public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.CommandViewHolder> {
    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public CommandAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(CommandQueryClassifier.Result result) {
        rows.clear();
        switch (result.getDisplayState()) {
            case COMMAND_HELP:
                rows.add(new Row(result.getMessage(), "", null, RowAction.NONE));
                addCommands(result.getCommands());
                break;
            case SUGGESTION:
                rows.add(new Row(result.getMessage(), "", null, RowAction.NONE));
                addCommands(result.getCommands());
                break;
            case PREVIEW:
                String query = "!" + result.getCommand().getName();
                if (!result.getArguments().isEmpty()) query += " " + result.getArguments();
                rows.add(new Row(query, result.getMessage() + "\n" + result.getSyntaxHint(), query,
                        RowAction.SUBMIT));
                break;
            case VALIDATION_ERROR:
                rows.add(new Row(result.getMessage(), result.getSyntaxHint(), null, RowAction.NONE));
                break;
            case UNKNOWN_COMMAND:
                rows.add(new Row(result.getMessage(), "Type one of these commands:", null,
                        RowAction.NONE));
                addCommands(result.getCommands());
                break;
            case APP_RESULTS:
                break;
        }
        notifyDataSetChanged();
    }

    public void showStatus(String title, String detail) {
        rows.clear();
        rows.add(new Row(title, detail, null, RowAction.NONE));
        notifyDataSetChanged();
    }

    public void showPermissionStatus(String title, String detail) {
        rows.clear();
        rows.add(new Row(title, detail, null, RowAction.OPEN_SETTINGS));
        notifyDataSetChanged();
    }

    private void addCommands(List<CommandQueryClassifier.Command> commands) {
        for (CommandQueryClassifier.Command command : commands) {
            String query = "!" + command.getName();
            rows.add(new Row(query, command.getSyntaxHint(), query + " ", RowAction.EDIT));
        }
    }

    @NonNull
    @Override
    public CommandViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.command_list_item, parent, false);
        return new CommandViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommandViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.title.setText(styledCommandText(holder.title.getContext(), row.title));
        holder.detail.setText(row.detail);
        holder.detail.setVisibility(row.detail.isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(null);
        if (row.action == RowAction.EDIT) {
            holder.itemView.setOnClickListener(view -> listener.onEdit(row.query));
        } else if (row.action == RowAction.SUBMIT) {
            holder.itemView.setOnClickListener(view -> listener.onSubmit(row.query));
        } else if (row.action == RowAction.OPEN_SETTINGS) {
            holder.itemView.setOnClickListener(view -> listener.onOpenSettings());
        }
    }

    static void styleCommandToken(Context context, Spannable text) {
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
            text.removeSpan(span);
        }
        if (text.length() == 0 || text.charAt(0) != '!') {
            return;
        }
        int end = 1;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        try (TypedArray attributes = context.obtainStyledAttributes(
                new int[]{androidx.appcompat.R.attr.colorAccent})) {
            text.setSpan(new ForegroundColorSpan(attributes.getColor(0, 0)), 0, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static CharSequence styledCommandText(Context context, String text) {
        SpannableString styled = new SpannableString(text);
        styleCommandToken(context, styled);
        return styled;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class CommandViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView detail;

        CommandViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.command_title);
            detail = itemView.findViewById(R.id.command_detail);
        }
    }

    public interface Listener {
        void onEdit(String query);

        void onSubmit(String query);

        void onOpenSettings();
    }

    private enum RowAction {
        NONE,
        EDIT,
        SUBMIT,
        OPEN_SETTINGS
    }

    private static class Row {
        private final String title;
        private final String detail;
        private final String query;
        private final RowAction action;

        Row(String title, String detail, String query, RowAction action) {
            this.title = title;
            this.detail = detail;
            this.query = query;
            this.action = action;
        }
    }
}
