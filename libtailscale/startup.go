// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"context"
	"sync"
)

// startupGate publishes initialization only after both backend phases succeed.
// A failure wakes all waiters, and a request deadline also bounds initialization.
type startupGate struct {
	mu        sync.Mutex
	done      chan struct{}
	remaining int
	err       error
}

func newStartupGate(phases int) *startupGate {
	return &startupGate{done: make(chan struct{}), remaining: phases}
}

func (g *startupGate) complete(err error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.remaining == 0 {
		return
	}
	if err != nil {
		g.err = err
		g.remaining = 0
	} else {
		g.remaining--
	}
	if g.remaining == 0 {
		close(g.done)
	}
}

func (g *startupGate) wait(ctx context.Context) error {
	select {
	case <-g.done:
		return g.err
	case <-ctx.Done():
		return ctx.Err()
	}
}
