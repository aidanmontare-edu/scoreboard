
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 * Penalty Timing (C) 2013 Rob Thomas (The G33k) <xrobau@gmail.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

$(function() {
  setupJamControlPage();
  setupPeriodTimePage();

  WS.AutoRegister();
  WS.Connect();
});

function isTrue(value) {
  if (typeof value === 'boolean') {
    return value;
  } else {
    return (String(value).toLowerCase() === 'true');
  }
}

function setupJamControlPage() {
  $('#JamControlPage button.StartJam').on('click', function() { WS.Set('ScoreBoard.CurrentGame.StartJam', true); });
  $('#JamControlPage button.StopJam').on('click', function() { WS.Set('ScoreBoard.CurrentGame.StopJam', true); });
  $('#JamControlPage button.Timeout').on('click', function() { WS.Set('ScoreBoard.CurrentGame.Timeout', true); });
  $('#JamControlPage button.Undo').on('click', function() { WS.Set('ScoreBoard.CurrentGame.ClockUndo', true); });
  $('#JamControlPage div.Timeout button.Official').on('click', function() { WS.Set('ScoreBoard.CurrentGame.OfficialTimeout', true); });
  $('#JamControlPage div.Timeout button.Team1').on('click', function() { WS.Set('ScoreBoard.CurrentGame.Team(1).Timeout', true); });
  $('#JamControlPage div.OfficialReview button.Team1').on('click', function() { WS.Set('ScoreBoard.CurrentGame.Team(1).OfficialReview', true); });
  $('#JamControlPage div.Timeout button.Team2').on('click', function() { WS.Set('ScoreBoard.CurrentGame.Team(2).Timeout', true); });
  $('#JamControlPage div.OfficialReview button.Team2').on('click', function() { WS.Set('ScoreBoard.CurrentGame.Team(2).OfficialReview', true); });

  WS.Register(['ScoreBoard.CurrentGame.Team(*).Name', 'ScoreBoard.CurrentGame.Team(*).UniformColor', 
               'ScoreBoard.CurrentGame.Team(*).AlternateName(operator)'], function(k, v) {
    var name = WS.state['ScoreBoard.CurrentGame.Team('+k.Team+').AlternateName(operator)'];
    name = name || WS.state['ScoreBoard.CurrentGame.Team('+k.Team+').UnifromColor'];
    if (name == null || name === '') {
      name = WS.state['ScoreBoard.CurrentGame.Team('+k.Team+').Name'];
    }
    $('.Name.Team'+k.Team).text(name);
  });

  // Setup clocks
  var showJamControlClock = function(clock) {
    $('#JamControlPage div.Time').not('.'+clock+'Time').hide().end()
      .filter('.'+clock+'Time').show();
  };
  // In case no clocks are running now, default to showing only Jam
  showJamControlClock('Jam');

  WS.Register('ScoreBoard.CurrentGame.Clock(*).Running', function(k, v) {
    $('#JamControlPage span.ClockBubble.'+k.Clock).toggleClass('Running', isTrue(v));
  });
  $.each( [ 'Start', 'Stop', 'Timeout', 'Undo' ], function(i, button) {
    WS.Register('ScoreBoard.CurrentGame.Label('+button+')', function(k, v) {
      $('#JamControlPage span.'+button+'Label').text(v);
    });
  });
  WS.Register('ScoreBoard.CurrentGame.Clock(*).Running', function(k, v) {
    if (isTrue(v)) {
      showJamControlClock(k.Clock);
    }
  });
  WS.Register('ScoreBoard.CurrentGame.Clock(*).Direction'); // for rounding
}

function setupPeriodTimePage() {
  $('#PeriodTimePage button.TimeDown').on('click', function() {
    WS.Set('ScoreBoard.CurrentGame.Clock(Period).Time', -1000, 'change');
  });
  $('#PeriodTimePage button.TimeUp').on('click', function() {
    WS.Set('ScoreBoard.CurrentGame.Clock(Period).Time', 1000, 'change');
  });
  $('#PeriodTimePage button.SetTime').on('click', function() {
    var t = $('#PeriodTimePage input:text.SetTime');
    WS.Set('ScoreBoard.CurrentGame.Clock(Period).Time', _timeConversions.minSecToMs(t.val()));
  });
}


function toTime(k, v) {
  k = WS._enrichProp(k);
  var isCountDown = isTrue(WS.state['ScoreBoard.CurrentGame.Clock(' + k.Clock + ').Direction']);
  return _timeConversions.msToMinSecNoZero(v, isCountDown);
}
//# sourceURL=nso\jt\index.js
