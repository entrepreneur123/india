"""Which draw a run waits for, and when the relay starts the next run (times UTC)."""
import datetime as dt

from lottery_pipeline import schedule as sc


def at(hhmm, day=27):
    h, m = map(int, hhmm.split(":"))
    return dt.datetime(2026, 10, day, h, m, tzinfo=dt.timezone.utc)


def test_on_time_runs_do_what_they_were_scheduled_for():
    assert sc.mode(at("05:49"), "schedule", "47 5 * * *")["require"] == "NL1PM"
    assert sc.mode(at("09:20"), "schedule", "17 9 * * *")["require"] == "KL"
    m = sc.mode(at("12:50"), "schedule", "47 12 * * *")
    assert m["require"] == "NL8PM" and m["from"] == "14:35" and m["mh"] is True
    assert sc.mode(at("15:12"), "schedule", "10 15 * * *")["deep"] is True
    overnight = sc.mode(at("03:20", 28), "schedule", "0 3 * * *")
    assert overnight["deep"] is True and overnight["backfill"] == 2


def test_a_late_run_does_what_is_due_now():
    # 27-10-2026: the 07:17 run started at 13:15. It must wait for the 8 PM draw, not re-check 1 PM.
    m = sc.mode(at("13:15"), "schedule", "17 7 * * *", done={"NL1PM", "KL", "SK6PM"})
    assert m["require"] == "NL8PM" and m["from"] == "14:35"
    assert sc.mode(at("13:15"), "schedule", "17 7 * * *", done={"NL1PM", "KL"})["require"] == "SK6PM"  # 6 PM still out
    assert sc.mode(at("15:30"), "schedule", "17 14 * * *")["require"] == "NL8PM"   # still polling 8 PM
    assert sc.mode(at("16:30"), "schedule", "17 14 * * *")["deep"] is True
    assert sc.mode(at("08:30", 28), "schedule", "0 3 * * *")["require"] == "NL1PM"  # 5 h late: 1 PM is due


def test_relay_and_hand_started_runs():
    assert sc.mode(at("07:20"), "workflow_dispatch", target="NL1PM")["require"] == "NL1PM"
    assert sc.mode(at("11:00"), "workflow_dispatch")["deep"] is True


def test_relay_wakes_15_minutes_before_each_draw_and_splits_long_waits():
    assert sc.relay(at("07:45")) == {"sleep": int((at("10:15") - at("07:45")).total_seconds()), "next": "results",
                                     "target": "KL"}
    assert sc.relay(at("12:00"))["target"] == "SK6PM" and sc.relay(at("12:00"))["sleep"] == 15 * 60
    assert sc.relay(at("12:14"))["target"] == "NL8PM"                 # too close to 12:15: the next one
    night = sc.relay(at("15:00"))                                      # tomorrow 07:15 is 16 h away
    assert night == {"sleep": int(sc.MAX_SLEEP.total_seconds()), "next": "relay", "target": ""}
    last = sc.relay(at("03:00", 28))
    assert last["next"] == "results" and last["target"] == "NL1PM" and last["sleep"] == 4 * 3600 + 15 * 60
