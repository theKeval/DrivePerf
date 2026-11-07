/* DrivePerf prototype engine · D1 in-drive gate (screen-map.md §2.2, accessibility.md §7 D1/D12).
   Every navigation (ctx.nav, deep links, catalog links, notification/widget taps) goes through DP.gate.resolve().
     live=<liveTrip state> (not passenger)  → a driver trip is active → only S-DRV-01/02/03 (+ S-DRV-05); target remembered
     moving=1 (trip ended, still automotive / > 10 km/h) and target is content → S-DRV-05, which opens the target after 60 s
     live=passenger                          → target allowed; the shell shows the recording banner
   OS surfaces (presentation 'live') are not app navigation and are never gated. */
(function () {
  const ALLOWED_IN_DRIVE = ['S-DRV-01', 'S-DRV-02', 'S-DRV-03', 'S-DRV-05'];
  // Story, Replay, Analysis, Recap, Wrapped, Share, Paywall (D12: no paywall/share during a trip)
  const CONTENT = /^S-(STY|RPL|ANL|RCP|WRP|SHR)-|^S-SUB-0[234]$/;
  const DRV01_STATE = { manual: 'default', auto: 'autoTrip', gpsSearching: 'gpsSearching', lowBattery: 'lowBatteryMode', passenger: 'passengerModeActive', cap8h: 'default' };

  function tripActive(params) {
    const s = params.live && window.DP_DATA?.liveTrip.states[params.live];
    if (!s) return null;
    return { key: params.live, passenger: !!s.passenger, state: s };
  }

  /** @returns {{id, state, params, redirected:boolean, reason?:string, pending?:{id,state,params}}} */
  function resolve(target, platform = 'ios') {
    const { id, state, params } = target;
    if (DP.presOf(id, platform) === 'live') return { ...target, redirected: false };
    const trip = tripActive(params);
    if (trip && !trip.passenger && !ALLOWED_IN_DRIVE.includes(id)) {
      return { id: 'S-DRV-01', state: DRV01_STATE[trip.key] || 'default', params, redirected: true, reason: 'driver trip active (D1)', pending: target };
    }
    if (params.moving === '1' && CONTENT.test(id)) {
      return { id: 'S-DRV-05', state: 'default', params, redirected: true, reason: 'held while moving (D1/D12)', pending: target };
    }
    return { ...target, redirected: false };
  }

  window.DP = window.DP || {};
  DP.gate = { resolve, tripActive, ALLOWED_IN_DRIVE, CONTENT };
})();
