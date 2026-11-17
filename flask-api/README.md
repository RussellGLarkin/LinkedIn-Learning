# flask-api

> **Training exercise. Do not deploy.** This app is intentionally written in a dated, insecure style so that students can practice analysis, testing, and refactoring with Claude Code. Treat it as legacy code you have inherited.

A small Flask web app with session-based login, per-user notes, image uploads, and an admin page for managing accounts. Data is stored in SQLite and pages are rendered with Jinja2 templates and Bootstrap.

For how the pieces fit together, see [ARCHITECTURE.md](ARCHITECTURE.md).

## How to Run

Requires Python 3.9 or newer (Flask 3.1's minimum).

```bash
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Then open <http://localhost:5000>.

The SQLite database files in `database_file/` and the sample uploads in `image_pool/` are committed, so the app runs with data already in place. There is no schema or seed script; if you modify the data and want it back, restore it with `git checkout -- database_file image_pool`.

## What the App Does

- **Public**: visible to anyone.
- **Private**: logged-in users only. Shows the user's notes and uploaded images, with forms to add and delete them. Anonymous visitors get a 401 page.
- **Admin Dashboard**: only for the `ADMIN` account. Lists, adds, and deletes accounts.

Test accounts: `admin` / `admin` and `test` / `123456`. User IDs are case-insensitive (stored uppercase).

## References

- Flask documentation: <https://flask.palletsprojects.com/>

## Credits

Image `static/img/public.jpg`: <https://commons.wikimedia.org/wiki/File:Drown%3F!_(131380682).jpg>
