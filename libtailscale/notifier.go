// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"context"
	"encoding/json"
	"log"
	"runtime/debug"

	"tailscale.com/ipn"
)

func (app *App) WatchNotifications(mask int, cb NotificationCallback) NotificationManager {

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		if err := app.ready.wait(ctx); err != nil {
			log.Printf("WatchNotifications: backend unavailable: %v", err)
			if cb != nil {
				message := err.Error()
				payload, _ := json.Marshal(&ipn.Notify{ErrMessage: &message})
				cb.OnNotify(payload)
			}
			cancel()
			return
		}
		app.backend.WatchNotifications(ctx, ipn.NotifyWatchOpt(mask), func() {}, func(notify *ipn.Notify) bool {
			defer func() {
				if p := recover(); p != nil {
					log.Printf("panic in WatchNotifications callback: %v\n%s", p, debug.Stack())
				}
			}()

			if notify == nil {
				return true
			}

			b, err := json.Marshal(notify)
			if err != nil {
				log.Printf("error: WatchNotifications: marshal notify: %s", err)
				return true
			}
			if cb == nil {
				return false
			}
			err = cb.OnNotify(b)
			if err != nil {
				log.Printf("error: WatchNotifications: OnNotify: %s", err)
				return true
			}
			return true
		})
	}()
	return &notificationManager{cancel}
}

type notificationManager struct {
	cancel func()
}

func (nm *notificationManager) Stop() {
	nm.cancel()
}
