// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestStartupFailureWakesAllWaiters(t *testing.T) {
	for _, completed := range []int{0, 1} {
		gate := newStartupGate(2)
		for range completed {
			gate.complete(nil)
		}
		want := errors.New("backend failed")
		result := make(chan error, 2)
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		for range 2 {
			go func() { result <- gate.wait(ctx) }()
		}
		gate.complete(want)
		gate.complete(nil) // The other initialization phase may finish after failure.
		for range 2 {
			if err := <-result; !errors.Is(err, want) {
				t.Fatalf("got %v, want %v", err, want)
			}
		}
	}
}

func TestStartupNeedsBothPhasesAndHonorsDeadline(t *testing.T) {
	gate := newStartupGate(2)
	gate.complete(nil)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := gate.wait(ctx); !errors.Is(err, context.Canceled) {
		t.Fatalf("got %v", err)
	}
	ctx, cancel = context.WithTimeout(context.Background(), time.Millisecond)
	defer cancel()
	if err := gate.wait(ctx); !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("got %v", err)
	}
	gate.complete(nil)
	gate.complete(errors.New("late duplicate"))
	if err := gate.wait(context.Background()); err != nil {
		t.Fatal(err)
	}
}
