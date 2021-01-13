/*
 *  Copyright 2021 CNM Ingenuity, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cnm.deepdive.gameoflife.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.OnLifecycleEvent;
import edu.cnm.deepdive.gameoflife.model.Terrain;
import java.util.Random;

public class MainViewModel extends AndroidViewModel implements LifecycleObserver {

  private static final int DEFAULT_TERRAIN_SIZE = 500;
  private static final int DEFAULT_DENSITY = 20;

  private final MutableLiveData<Terrain> terrain;
  private final MutableLiveData<Long> generation;
  private final MutableLiveData<Integer> population;
  private final MutableLiveData<Boolean> running;
  private final MutableLiveData<Integer> density;
  private final Random rng;

  private Runner runner;

  public MainViewModel(@NonNull Application application) {
    super(application);
    terrain = new MutableLiveData<>(null);
    generation = new MutableLiveData<>(0L);
    population = new MutableLiveData<>(0);
    running = new MutableLiveData<>(false);
    density = new MutableLiveData<>(DEFAULT_DENSITY);
    rng = new Random();
    reset();
  }

  public LiveData<Terrain> getTerrain() {
    return terrain;
  }

  public LiveData<Long> getGeneration() {
    return generation;
  }

  public LiveData<Integer> getPopulation() {
    return population;
  }

  public LiveData<Boolean> getRunning() {
    return running;
  }

  public MutableLiveData<Integer> getDensity() {
    return density;
  }

  public void start() {
    stopRunner(false);
    running.setValue(true);
    startRunner();
  }

  public void stop() {
    stopRunner(true);
    running.setValue(false);
  }

  public void reset() {
    stop();
    //noinspection ConstantConditions
    Terrain terrain = new Terrain(DEFAULT_TERRAIN_SIZE, density.getValue() / 100d, rng);
    this.terrain.setValue(terrain);
    generation.setValue(terrain.getIterationCount());
  }

  @OnLifecycleEvent(Event.ON_PAUSE)
  private void pause() {
    //noinspection ConstantConditions
    stopRunner(!running.getValue());
  }

  @OnLifecycleEvent(Event.ON_RESUME)
  private void resume() {
    //noinspection ConstantConditions
    if (running.getValue()) {
      startRunner();
    }
  }

  private void startRunner() {
    runner = new Runner();
    runner.start();
  }

  private void stopRunner(boolean postOnStop) {
    if (runner != null) {
      runner.setPostOnStop(postOnStop);
      runner.setRunning(false);
      runner = null;
    }
  }

  private class Runner extends Thread {

    private boolean running = true;
    private boolean postOnStop;

    @Override
    public void run() {
      while (running) {
        Terrain terrain = MainViewModel.this.terrain.getValue();
        if (terrain != null) {
          terrain.iterate();
          generation.postValue(terrain.getIterationCount());
          population.postValue(terrain.getPopulation());
        }
      }
      if (postOnStop) {
        MainViewModel.this.running.postValue(false);
      }
    }

    public void setRunning(boolean running) {
      this.running = running;
    }

    public void setPostOnStop(boolean postOnStop) {
      this.postOnStop = postOnStop;
    }

  }

}
