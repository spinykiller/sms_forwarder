package com.enixcoda.smsforward;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pro: list and edit routing rules. Pro-only screen, so no ad. */
public class RulesActivity extends AppCompatActivity {
    private static final int[] CHECKBOXES = {R.id.rule_sms, R.id.rule_telegram, R.id.rule_web, R.id.rule_email};

    private List<Rules.Rule> rules;
    private ListView list;
    private View empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);
        SystemBars.apply(this, findViewById(R.id.header));
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        rules = Rules.load(this);
        list.setOnItemClickListener((parent, view, position, id) -> edit(rules.get(position)));
        findViewById(R.id.add).setOnClickListener(v -> edit(null));
        render();
    }

    private void render() {
        empty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rules.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new ArrayAdapter<Rules.Rule>(this, android.R.layout.simple_list_item_2, android.R.id.text1, rules) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                Rules.Rule rule = getItem(position);
                List<String> methods = new ArrayList<>();
                for (String m : Rules.METHODS) if (rule.methods.contains(m)) methods.add(Forwarder.label(m));
                ((TextView) row.findViewById(android.R.id.text1)).setText(getString(R.string.rule_row_title, rule.name, String.join(", ", methods)));
                ((TextView) row.findViewById(android.R.id.text2)).setText(getString(R.string.rule_row_body,
                        rule.senders.isEmpty() ? getString(R.string.rule_any) : rule.senders,
                        rule.keywords.isEmpty() ? getString(R.string.rule_any) : rule.keywords));
                return row;
            }
        });
    }

    /** rule null = new rule */
    private void edit(Rules.Rule rule) {
        View form = getLayoutInflater().inflate(R.layout.dialog_rule, null);
        EditText name = form.findViewById(R.id.rule_name);
        EditText senders = form.findViewById(R.id.rule_senders);
        EditText keywords = form.findViewById(R.id.rule_keywords);
        if (rule != null) {
            name.setText(rule.name);
            senders.setText(rule.senders);
            keywords.setText(rule.keywords);
            for (int i = 0; i < CHECKBOXES.length; i++) {
                ((CheckBox) form.findViewById(CHECKBOXES[i])).setChecked(rule.methods.contains(Rules.METHODS[i]));
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(rule == null ? R.string.rules_add : R.string.rule_edit)
                .setView(form)
                .setPositiveButton(R.string.rule_save, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (rule != null) builder.setNeutralButton(R.string.rule_delete, (d, w) -> {
            rules.remove(rule);
            Rules.save(this, rules);
            render();
        });
        AlertDialog dialog = builder.show();
        // Validate before closing: a bad rule would silently route messages nowhere
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String invalid = MessageRules.invalidRegex(MessageRules.splitKeywords(keywords.getText().toString()));
            if (invalid != null) {
                Toast.makeText(this, getString(R.string.invalid_regex, invalid), Toast.LENGTH_LONG).show();
                return;
            }
            Set<String> methods = new HashSet<>();
            for (int i = 0; i < CHECKBOXES.length; i++) {
                if (((CheckBox) form.findViewById(CHECKBOXES[i])).isChecked()) methods.add(Rules.METHODS[i]);
            }
            if (methods.isEmpty()) {
                Toast.makeText(this, R.string.rule_no_methods, Toast.LENGTH_LONG).show();
                return;
            }
            Rules.Rule target = rule != null ? rule : new Rules.Rule();
            target.methods.clear();
            target.methods.addAll(methods);
            target.name = name.getText().toString().trim().isEmpty() ? getString(R.string.rule_default_name, rules.size() + 1) : name.getText().toString().trim();
            target.senders = senders.getText().toString().trim();
            target.keywords = keywords.getText().toString().trim();
            if (rule == null) rules.add(target);
            Rules.save(this, rules);
            render();
            dialog.dismiss();
        });
    }
}
