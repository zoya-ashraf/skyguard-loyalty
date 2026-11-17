import unittest
from app import score, PLACES


class RiskTests(unittest.TestCase):
    def test_same_city_is_safe(self):
        self.assertEqual(score("Mumbai", "Mumbai")["risk"], 0.1)

    def test_nearby_city_is_low(self):
        self.assertLess(score("Mumbai", "Pune")["risk"], 0.5)

    def test_far_city_is_risky(self):
        self.assertGreaterEqual(score("Mumbai", "Delhi")["risk"], 0.5)

    def test_other_country_is_very_risky(self):
        self.assertGreaterEqual(score("Mumbai", "London")["risk"], 0.9)

    def test_unknown_city(self):
        self.assertEqual(score("Mumbai", "Atlantis")["distanceKm"], -1)

    def test_case_does_not_matter(self):
        self.assertEqual(score("mumbai", "DELHI"), score("Mumbai", "Delhi"))

    def test_every_city_scores(self):
        for city in PLACES:
            self.assertIn("risk", score("Ranchi", city))


if __name__ == "__main__":
    unittest.main()
