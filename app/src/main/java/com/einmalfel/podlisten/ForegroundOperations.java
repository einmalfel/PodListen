package com.einmalfel.podlisten;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;


/**
 * This service handles operations that user expects to be done fast (like marking new eps leaving)
 */
public class ForegroundOperations extends IntentService {
  private static final String TAG = "FGS";

  private static final String ACTION_SET_STATE = "com.einmalfel.podlisten.SET_STATE";

  private static final String EXTRA_EPISODE_STATE = "com.einmalfel.podlisten.EPISODE_STATE";
  private static final String EXTRA_EPISODE_FILTER = "com.einmalfel.podlisten.EPISODE_FILTER";

  public ForegroundOperations() {
    super("ForegroundOperations");
    setIntentRedelivery(true);
  }

  public static void setEpisodesState(@NonNull Context context, int state, int stateFilter) {
    Intent intent = new Intent(context, ForegroundOperations.class);
    intent.setAction(ACTION_SET_STATE);
    intent.putExtra(EXTRA_EPISODE_FILTER, stateFilter);
    intent.putExtra(EXTRA_EPISODE_STATE, state);
    context.startService(intent);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent != null) {
      final String action = intent.getAction();
      Log.i(TAG, "Processing " + action);
      switch (action) {
        case ACTION_SET_STATE:
          setEpisodesState(intent.getIntExtra(EXTRA_EPISODE_STATE, Provider.ESTATE_GONE),
                           intent.getIntExtra(EXTRA_EPISODE_FILTER, Provider.ESTATE_GONE));
          break;
        default:
          Log.wtf(TAG, "Unexpected intent action: " + action);
      }
      Log.i(TAG, "Finished processing of " + action);
    }
  }

  private void setEpisodesState(int state, int stateFilter) {
    ContentValues cv = new ContentValues(1);
    cv.put(Provider.K_ESTATE, state);
    int result = getContentResolver().update(Provider.episodeUri,
                                             cv,
                                             Provider.K_ESTATE + " == " + stateFilter,
                                             null);
    Log.i(TAG, "Switched state from " + stateFilter + " to " + state + " for " + result + " eps");
  }
}
